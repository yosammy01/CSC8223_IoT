/**
 * Collects client model updates, performs FedAvg, persists snapshots, and
 * publishes the resulting global model. See README.md for the workflow.
 */
package com.samuel_robles.java_backend.federated;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@Service
public class FederatedAggregationService {

    private final MqttPublisher mqttPublisher;
    private final int expectedClients;
    private final String globalWeightsTopic;
    private final FederatedModelSnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, float[]> pendingUpdates = new LinkedHashMap<>();
    private final Map<String, float[]> latestClientWeights = new LinkedHashMap<>();
    private float[] latestGlobalWeights;
    private Instant lastAggregationAt;
    private final AtomicLong receivedUpdates = new AtomicLong();
    private final AtomicLong completedRounds = new AtomicLong();

    public FederatedAggregationService(
            MqttPublisher mqttPublisher,
            @Value("${app.federated.expected-clients:2}") int expectedClients,
            @Value("${app.mqtt.global-weights-topic:federated/global/weights}") String globalWeightsTopic,
            @Value("${app.mqtt.local-weights-topic:federated/local/weights}") String localWeightsTopic,
            FederatedModelSnapshotRepository snapshotRepository,
            JdbcTemplate jdbcTemplate) {
        if (expectedClients < 1) {
            throw new IllegalArgumentException("app.federated.expected-clients must be at least 1.");
        }
        this.mqttPublisher = mqttPublisher;
        this.expectedClients = expectedClients;
        this.globalWeightsTopic = globalWeightsTopic;
        this.snapshotRepository = snapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        migratePayloadColumn();
        snapshotRepository.deleteByModelType("LOCAL");
        loadPersistedModels();
    }

    private void migratePayloadColumn() {
        String dataType = jdbcTemplate.queryForObject(
                """
                select udt_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'federated_model_snapshots'
                  and column_name = 'payload'
                """,
                String.class);
        if ("oid".equals(dataType)) {
            jdbcTemplate.execute(
                    "alter table federated_model_snapshots "
                            + "alter column payload type bytea using lo_get(payload)");
        }
    }

    private void loadPersistedModels() {
        snapshotRepository.findBySnapshotKey("GLOBAL").ifPresent(snapshot -> {
            try {
                float[] weights = QuantizedWeightsCodec.decode(snapshot.getPayload());
                if (weights.length == QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT) {
                    latestGlobalWeights = weights;
                    lastAggregationAt = snapshot.getUpdatedAt();
                } else {
                    snapshotRepository.deleteById("GLOBAL");
                }
            } catch (IllegalArgumentException incompatibleSnapshot) {
                snapshotRepository.deleteById("GLOBAL");
            }
        });
    }

    public synchronized void accept(String clientId, byte[] payload) {
        float[] update = QuantizedWeightsCodec.decode(payload);
        if (update.length != QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT) {
            throw new IllegalArgumentException("Federated updates must contain exactly 502 weights.");
        }
        receivedUpdates.incrementAndGet();
        latestClientWeights.put(clientId, update);
        pendingUpdates.put(clientId, update);
        if (latestClientWeights.size() < expectedClients) {
            return;
        }

        float[] aggregate = new float[update.length];
        int updateCount = latestClientWeights.size();
        for (float[] clientUpdate : latestClientWeights.values()) {
            if (clientUpdate.length != aggregate.length) {
                pendingUpdates.clear();
                latestClientWeights.clear();
                throw new IllegalArgumentException(
                        "All models in a federated round must have the same weight count.");
            }
            for (int i = 0; i < aggregate.length; i++) {
                aggregate[i] += clientUpdate[i] / updateCount;
            }
        }
        pendingUpdates.clear();
        latestGlobalWeights = aggregate;
        lastAggregationAt = Instant.now();
        snapshotRepository.save(new FederatedModelSnapshot(
                "GLOBAL", "GLOBAL", null, QuantizedWeightsCodec.encode(aggregate), lastAggregationAt));
        completedRounds.incrementAndGet();
        mqttPublisher.publish(
                QuantizedWeightsCodec.encode(aggregate),
                globalWeightsTopic,
                1,
                true);
    }

    public synchronized void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("A client ID is required for local federated weights.");
        }
    }

    public synchronized void republishAllModels() {
        republishLatestGlobalWeights();
    }

    public synchronized void resetAggregationStatus() {
        pendingUpdates.clear();
        latestClientWeights.clear();
        receivedUpdates.set(0);
        completedRounds.set(0);
        lastAggregationAt = null;
    }

    public synchronized void republishLatestGlobalWeights() {
        if (latestGlobalWeights == null) {
            throw new IllegalStateException("No global model has been aggregated yet.");
        }
        mqttPublisher.publish(
                QuantizedWeightsCodec.encode(latestGlobalWeights),
                globalWeightsTopic,
                1,
                true);
    }

    public synchronized FederatedStatus status() {
        return new FederatedStatus(
                receivedUpdates.get(),
                completedRounds.get(),
                pendingUpdates.size(),
                expectedClients,
                latestGlobalWeights == null ? 0 : latestGlobalWeights.length,
                lastAggregationAt);
    }

    public record FederatedStatus(
            long receivedUpdates,
            long completedRounds,
            int pendingUpdates,
            int expectedClients,
            int globalWeightCount,
            Instant lastAggregationAt) {
    }
}

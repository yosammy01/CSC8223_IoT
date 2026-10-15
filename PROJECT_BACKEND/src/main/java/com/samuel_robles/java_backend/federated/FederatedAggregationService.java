package com.samuel_robles.java_backend.federated;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@Service
public class FederatedAggregationService {

    private final MqttPublisher mqttPublisher;
    private final int expectedClients;
    private final String globalWeightsTopic;
    private final String localWeightsTopic;
    private final FederatedModelSnapshotRepository snapshotRepository;
    private final Map<String, float[]> pendingUpdates = new LinkedHashMap<>();
    private final Map<String, byte[]> latestLocalWeights = new LinkedHashMap<>();
    private float[] latestGlobalWeights;
    private Instant lastAggregationAt;
    private final AtomicLong receivedUpdates = new AtomicLong();
    private final AtomicLong completedRounds = new AtomicLong();

    public FederatedAggregationService(
            MqttPublisher mqttPublisher,
            @Value("${app.federated.expected-clients:2}") int expectedClients,
            @Value("${app.mqtt.global-weights-topic:federated/global/weights}") String globalWeightsTopic,
            @Value("${app.mqtt.local-weights-topic:federated/local/weights}") String localWeightsTopic,
            FederatedModelSnapshotRepository snapshotRepository) {
        if (expectedClients < 1) {
            throw new IllegalArgumentException("app.federated.expected-clients must be at least 1.");
        }
        this.mqttPublisher = mqttPublisher;
        this.expectedClients = expectedClients;
        this.globalWeightsTopic = globalWeightsTopic;
        this.snapshotRepository = snapshotRepository;
        this.localWeightsTopic = localWeightsTopic.endsWith("/#")
                ? localWeightsTopic.substring(0, localWeightsTopic.length() - 2)
                : localWeightsTopic;
        loadPersistedModels();
    }

    private void loadPersistedModels() {
        snapshotRepository.findBySnapshotKey("GLOBAL").ifPresent(snapshot -> {
            latestGlobalWeights = QuantizedWeightsCodec.decode(snapshot.getPayload());
            lastAggregationAt = snapshot.getUpdatedAt();
        });
        for (FederatedModelSnapshot snapshot : snapshotRepository.findByModelType("LOCAL")) {
            latestLocalWeights.put(snapshot.getClientId(), snapshot.getPayload());
        }
    }

    public synchronized void accept(String clientId, byte[] payload) {
        cacheLocalWeights(clientId, payload);
        float[] update = QuantizedWeightsCodec.decode(payload);
        receivedUpdates.incrementAndGet();
        pendingUpdates.put(clientId, update);
        if (pendingUpdates.size() < expectedClients) {
            return;
        }

        float[] aggregate = new float[update.length];
        int updateCount = pendingUpdates.size();
        for (float[] clientUpdate : pendingUpdates.values()) {
            if (clientUpdate.length != aggregate.length) {
                pendingUpdates.clear();
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

    public synchronized void cacheLocalWeights(String clientId, byte[] payload) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("A client ID is required for local federated weights.");
        }
        latestLocalWeights.put(clientId, payload.clone());
        snapshotRepository.save(new FederatedModelSnapshot(
                "LOCAL:" + clientId, "LOCAL", clientId, payload, Instant.now()));
    }

    public synchronized void republishLatestLocalWeights() {
        for (Map.Entry<String, byte[]> entry : latestLocalWeights.entrySet()) {
            mqttPublisher.publish(
                    entry.getValue(),
                    localWeightsTopic + "/" + entry.getKey(),
                    1,
                    true);
        }
    }

    public synchronized void republishAllModels() {
        republishLatestGlobalWeights();
        republishLatestLocalWeights();
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

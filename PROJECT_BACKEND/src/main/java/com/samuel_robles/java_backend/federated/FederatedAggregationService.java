package com.samuel_robles.java_backend.federated;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@Service
public class FederatedAggregationService {

    private final MqttPublisher mqttPublisher;
    private final int expectedClients;
    private final String globalWeightsTopic;
    private final float[][] pendingUpdates;
    private int pendingCount;
    private float[] latestGlobalWeights;
    private Instant lastAggregationAt;
    private final AtomicLong receivedUpdates = new AtomicLong();
    private final AtomicLong completedRounds = new AtomicLong();

    public FederatedAggregationService(
            MqttPublisher mqttPublisher,
            @Value("${app.federated.expected-clients:2}") int expectedClients,
            @Value("${app.mqtt.global-weights-topic:federated/global/weights}") String globalWeightsTopic) {
        if (expectedClients < 1) {
            throw new IllegalArgumentException("app.federated.expected-clients must be at least 1.");
        }
        this.mqttPublisher = mqttPublisher;
        this.expectedClients = expectedClients;
        this.globalWeightsTopic = globalWeightsTopic;
        this.pendingUpdates = new float[expectedClients][];
    }

    public synchronized void accept(byte[] payload) {
        float[] update = QuantizedWeightsCodec.decode(payload);
        receivedUpdates.incrementAndGet();
        if (pendingCount > 0 && update.length != pendingUpdates[0].length) {
            throw new IllegalArgumentException("All models in a federated round must have the same weight count.");
        }

        pendingUpdates[pendingCount++] = update;
        if (pendingCount < expectedClients) {
            return;
        }

        float[] aggregate = new float[update.length];
        for (int client = 0; client < pendingCount; client++) {
            for (int i = 0; i < aggregate.length; i++) {
                aggregate[i] += pendingUpdates[client][i] / pendingCount;
            }
            pendingUpdates[client] = null;
        }
        pendingCount = 0;
        latestGlobalWeights = aggregate;
        lastAggregationAt = Instant.now();
        completedRounds.incrementAndGet();
        mqttPublisher.publish(
                QuantizedWeightsCodec.encode(aggregate),
                globalWeightsTopic,
                1,
                false);
    }

    public synchronized FederatedStatus status() {
        return new FederatedStatus(
                receivedUpdates.get(),
                completedRounds.get(),
                pendingCount,
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

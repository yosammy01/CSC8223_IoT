/**
 * Verifies that FedAvg waits for one update from each client in every round.
 */
package com.samuel_robles.java_backend.federated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.samuel_robles.java_backend.mqtt.MqttPublisher;

class FederatedAggregationServiceTest {

    @Test
    void waitsForBothClientsInEveryRound() {
        MqttPublisher mqttPublisher = mock(MqttPublisher.class);
        FederatedModelSnapshotRepository snapshotRepository = mock(FederatedModelSnapshotRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("bytea");
        when(snapshotRepository.findBySnapshotKey("GLOBAL")).thenReturn(Optional.empty());

        FederatedAggregationService service = new FederatedAggregationService(
                mqttPublisher, 2, "federated/global/weights", "federated/local/weights",
                snapshotRepository, jdbcTemplate);

        service.accept("client-1", update(1.0f));
        assertThat(service.status().pendingUpdates()).isEqualTo(1);
        assertThat(service.status().completedRounds()).isZero();
        verifyNoInteractions(mqttPublisher);

        service.accept("client-2", update(3.0f));
        assertThat(service.status().pendingUpdates()).isZero();
        assertThat(service.status().completedRounds()).isEqualTo(1);
        verify(mqttPublisher).publish(any(byte[].class),
                eq("federated/global/weights"), eq(1), eq(true));

        service.accept("client-1", update(5.0f));
        assertThat(service.status().pendingUpdates()).isEqualTo(1);
        assertThat(service.status().completedRounds()).isEqualTo(1);
        service.accept("client-1", update(9.0f));
        assertThat(service.status().pendingUpdates()).isEqualTo(1);
        assertThat(service.status().completedRounds()).isEqualTo(1);

        service.accept("client-2", update(7.0f));
        assertThat(service.status().pendingUpdates()).isZero();
        assertThat(service.status().completedRounds()).isEqualTo(2);
        verify(mqttPublisher, times(2)).publish(
                any(byte[].class),
                eq("federated/global/weights"), eq(1), eq(true));

        service.deleteGlobalModel();
        assertThat(service.status().pendingUpdates()).isZero();
        assertThat(service.status().completedRounds()).isZero();
        assertThat(service.status().globalWeightCount()).isZero();
        assertThat(service.status().lastAggregationAt()).isNull();
        verify(snapshotRepository).deleteById("GLOBAL");
        verify(mqttPublisher).publish(eq(new byte[0]), eq("federated/global/weights"), eq(1), eq(true));
        assertThatThrownBy(service::republishLatestGlobalWeights)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No global model");
    }

    private static byte[] update(float value) {
        ByteBuffer payload = ByteBuffer.allocate(QuantizedWeightsCodec.RAW_PAYLOAD_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT; i++) {
            payload.putFloat(value);
        }
        return payload.array();
    }
}

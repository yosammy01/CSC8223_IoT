/**
 * Verifies compatibility and round-trip behavior of model quantization.
 * See README.md for the wire format used by Android and the backend.
 */
package com.samuel_robles.java_backend.federated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

class QuantizedWeightsCodecTest {

    @Test
    void decodesAndroidBigEndianPayloadAsSignedBytes() {
        byte[] payload = ByteBuffer.allocate(QuantizedWeightsCodec.QUANTIZED_PAYLOAD_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(2.0f)
                .put((byte) -127)
                .put((byte) 0)
                .put((byte) 127)
                .put(new byte[QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT - 3])
                .array();

        assertThat(QuantizedWeightsCodec.decode(payload))
                .hasSize(QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT)
                .startsWith(-2.0f, 0.0f, 2.0f);
    }

    @Test
    void encodedPayloadRoundTripsWithinQuantizationPrecision() {
        float[] weights = new float[QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT];
        weights[0] = -2.0f;
        weights[1] = 0.0f;
        weights[2] = 1.0f;

        assertThat(QuantizedWeightsCodec.decode(QuantizedWeightsCodec.encode(weights)))
                .hasSize(QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT)
                .startsWith(-2.0f, 0.0f, 0.992126f);
    }

    @Test
    void decodes502WeightRawBigEndianPayload() {
        byte[] payload = ByteBuffer.allocate(QuantizedWeightsCodec.RAW_PAYLOAD_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(1.25f)
                .put(new byte[QuantizedWeightsCodec.RAW_PAYLOAD_SIZE - Float.BYTES])
                .array();

        float[] decoded = QuantizedWeightsCodec.decode(payload);

        assertThat(decoded).hasSize(QuantizedWeightsCodec.EXPECTED_WEIGHT_COUNT);
        assertThat(decoded[0]).isEqualTo(1.25f);
    }

    @Test
    void rejectsLegacyModelPayloadSizes() {
        assertThatThrownBy(() -> QuantizedWeightsCodec.decode(new byte[250]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("506 bytes")
                .hasMessageContaining("2008 bytes");
    }
}

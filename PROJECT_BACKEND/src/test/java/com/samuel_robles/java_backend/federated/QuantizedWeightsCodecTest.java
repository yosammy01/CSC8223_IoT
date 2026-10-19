/**
 * Verifies compatibility and round-trip behavior of model quantization.
 * See README.md for the wire format used by Android and the backend.
 */
package com.samuel_robles.java_backend.federated;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

class QuantizedWeightsCodecTest {

    @Test
    void decodesAndroidBigEndianPayloadAsSignedBytes() {
        byte[] payload = ByteBuffer.allocate(7)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(2.0f)
                .put(new byte[] {-127, 0, 127})
                .array();

        assertThat(QuantizedWeightsCodec.decode(payload))
                .containsExactly(-2.0f, 0.0f, 2.0f);
    }

    @Test
    void encodedPayloadRoundTripsWithinQuantizationPrecision() {
        byte[] payload = QuantizedWeightsCodec.encode(new float[] {-2.0f, 0.0f, 1.0f});

        assertThat(QuantizedWeightsCodec.decode(payload))
                .containsExactly(-2.0f, 0.0f, 0.992126f);
    }
}

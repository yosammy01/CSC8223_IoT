/**
 * Encodes and decodes the Android-compatible symmetric 8-bit model format.
 * See README.md for the wire format and quantized aggregation flow.
 */
package com.samuel_robles.java_backend.federated;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Codec for the Android Quantizer.kt wire format.
 */
public final class QuantizedWeightsCodec {

    private QuantizedWeightsCodec() {
    }

    public static float[] decode(byte[] payload) {
        if (payload == null || payload.length <= Float.BYTES) {
            throw new IllegalArgumentException("A quantized model must contain maxAbs and at least one weight.");
        }

        float maxAbs = ByteBuffer.wrap(payload, 0, Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getFloat();
        if (!Float.isFinite(maxAbs) || maxAbs <= 0.0f) {
            throw new IllegalArgumentException("Quantized model maxAbs must be a positive finite float.");
        }

        float scale = maxAbs / 127.0f;
        float[] weights = new float[payload.length - Float.BYTES];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = payload[Float.BYTES + i] * scale;
        }
        return weights;
    }

    public static byte[] encode(float[] weights) {
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("Cannot quantize an empty model.");
        }

        float maxAbs = 0.0f;
        for (float weight : weights) {
            if (!Float.isFinite(weight)) {
                throw new IllegalArgumentException("Model weights must be finite.");
            }
            maxAbs = Math.max(maxAbs, Math.abs(weight));
        }
        if (maxAbs == 0.0f) {
            maxAbs = 1.0e-9f;
        }

        byte[] payload = new byte[Float.BYTES + weights.length];
        ByteBuffer.wrap(payload, 0, Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(maxAbs);
        float scale = 127.0f / maxAbs;
        for (int i = 0; i < weights.length; i++) {
            float scaled = Math.clamp(weights[i] * scale, -127.0f, 127.0f);
            payload[Float.BYTES + i] = (byte) scaled;
        }
        return payload;
    }
}

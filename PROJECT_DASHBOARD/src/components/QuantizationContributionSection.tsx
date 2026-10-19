/**
 * ============================================================================
 * FILE README: src/components/QuantizationContributionSection.tsx
 * ============================================================================
 * Purpose:
 *   Presents and evaluates the project's Required Technical Contribution
 *   (Section 8 of the CSC 8223 specification): Communication-Efficient 8-Bit
 *   Symmetric Min-Max Weight Quantization.
 *
 * Key Features:
 *   1. Wire Format Breakdown: Visualizes the 32-bit float baseline payload
 *      (246 * 4 B = 984 Bytes) vs. the 8-bit quantized wire format implemented
 *      in `Quantizer.kt` and `QuantizedWeightsCodec.java` (4-byte Big-Endian
 *      `maxAbs` float header + 246 signed `int8` bytes = 250 Bytes), achieving
 *      a 74.59% payload reduction (3.94x compression ratio).
 *   2. Cumulative MQTT Bandwidth Calculator: Computes total bytes transmitted
 *      and saved across live or projected Federated Averaging rounds.
 *   3. Interactive Codec Inspector: Runs sample MLP weights through the exact
 *      symmetric min-max quantization/dequantization formula and displays
 *      per-weight reconstruction error.
 * ============================================================================
 */
import { useState } from 'react';
import {
  COMPRESSION_RATIO,
  FLOAT32_PAYLOAD_BYTES,
  INT8_QUANTIZED_PAYLOAD_BYTES,
  MLP_TOTAL_WEIGHTS,
  PAYLOAD_REDUCTION_PERCENT,
  type FederatedStatus,
} from '../types/federated';
import { simulateSymmetricQuantization } from '../data/evaluationData';

interface QuantizationContributionSectionProps {
  federatedStatus: FederatedStatus;
  global8BitAccuracy: number;
  global32BitAccuracy: number;
  global8BitMacroF1: number;
  global32BitMacroF1: number;
}

const DEFAULT_SAMPLE_WEIGHTS = [
  -1.842, 0.915, -0.438, 0.124, 1.56, -1.105, 0.032, 0.778,
];

export default function QuantizationContributionSection({
  federatedStatus,
  global8BitAccuracy,
  global32BitAccuracy,
  global8BitMacroF1,
  global32BitMacroF1,
}: QuantizationContributionSectionProps) {
  const [projectedRounds, setProjectedRounds] = useState<number>(15);
  const [sampleWeights, setSampleWeights] = useState<number[]>(
    DEFAULT_SAMPLE_WEIGHTS
  );

  // Each completed round consists of expectedClients local uploads + 1 global broadcast
  const effectiveRounds =
    federatedStatus.completedRounds > 0
      ? federatedStatus.completedRounds
      : projectedRounds;
  const messagesPerRound = federatedStatus.expectedClients + 1; // 2 uploads + 1 broadcast = 3 MQTT binary payloads
  const totalMessages = effectiveRounds * messagesPerRound;

  const totalBaselineBytes = totalMessages * FLOAT32_PAYLOAD_BYTES;
  const totalQuantizedBytes = totalMessages * INT8_QUANTIZED_PAYLOAD_BYTES;
  const totalSavedBytes = totalBaselineBytes - totalQuantizedBytes;

  const accuracyDelta = Math.abs(global32BitAccuracy - global8BitAccuracy);

  const quantSim = simulateSymmetricQuantization(sampleWeights);

  const randomizeSampleWeights = () => {
    const next = Array.from({ length: 8 }, () =>
      Number(((Math.random() * 4 - 2) * 0.95).toFixed(4))
    );
    setSampleWeights(next);
  };

  return (
    <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
      {/* Section Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-slate-900">
              Technical Contribution: 8-Bit Symmetric Min-Max Weight Quantization
            </h2>
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-purple-100 text-purple-800 border border-purple-200">
              Communication-Efficient FL
            </span>
          </div>
          <p className="text-sm text-slate-600 mt-1">
            Compresses dense 32-bit floating-point MLP weight arrays (
            <code className="font-mono text-xs">{MLP_TOTAL_WEIGHTS} floats</code>) into
            compact 8-bit signed integer MQTT payloads via{' '}
            <code className="font-mono text-xs">Quantizer.kt</code> &amp;{' '}
            <code className="font-mono text-xs">QuantizedWeightsCodec.java</code>.
          </p>
        </div>

        <div className="flex items-center gap-2 bg-purple-50 border border-purple-200 px-3.5 py-2 rounded-xl shrink-0">
          <div>
            <div className="text-[10px] font-bold uppercase text-purple-700">
              Payload Size Reduction
            </div>
            <div className="text-xl font-extrabold text-purple-900 font-mono">
              {PAYLOAD_REDUCTION_PERCENT.toFixed(2)}% ({COMPRESSION_RATIO.toFixed(2)}×)
            </div>
          </div>
        </div>
      </div>

      {/* Top Grid: Wire Format Diagram + Payload Size & Accuracy Trade-off */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left 6 Cols: Payload Byte Comparison */}
        <div className="lg:col-span-6 space-y-4">
          <h3 className="text-sm font-bold uppercase tracking-wider text-slate-700">
            MQTT Binary Wire Format &amp; Single-Update Payload Comparison
          </h3>

          {/* 32-bit Baseline Bar */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-slate-800">
                Baseline 32-Bit IEEE-754 Float Array (Uncompressed)
              </span>
              <span className="font-mono font-extrabold text-slate-900">
                {FLOAT32_PAYLOAD_BYTES} Bytes / update
              </span>
            </div>
            <div className="w-full h-7 bg-blue-600 rounded-lg flex items-center justify-center text-[11px] font-mono font-bold text-white shadow-inner">
              246 × 4-Byte Float32 Weights = 984 Bytes (100%)
            </div>
            <div className="text-[11px] text-slate-500">
               Breakdown: W1 (128×4B = 512B) + b1 (16×4B = 64B) + W2 (96×4B = 384B) + b2
              (6×4B = 24B)
            </div>
          </div>

          {/* 8-bit Quantized Bar */}
          <div className="p-4 rounded-xl bg-indigo-50/60 border border-indigo-200 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-indigo-950">
                Implemented 8-Bit Symmetric Min-Max Quantized Wire Format
              </span>
              <span className="font-mono font-extrabold text-indigo-700">
                {INT8_QUANTIZED_PAYLOAD_BYTES} Bytes / update (-74.59%)
              </span>
            </div>
            <div className="w-full bg-slate-200 rounded-lg h-7 overflow-hidden flex">
              <div
                style={{ width: '4%' }}
                title="4-Byte Big-Endian Float32 Header (maxAbs)"
                className="bg-amber-500 h-full flex items-center justify-center text-[9px] font-mono font-bold text-slate-950"
              >
                4B
              </div>
              <div
                style={{ width: '21.4%' }}
                title="246 × 1-Byte Signed Int8 Weights [-127, 127]"
                className="bg-indigo-600 h-full flex items-center justify-center text-[10px] font-mono font-bold text-white"
              >
                246B (int8)
              </div>
              <div className="flex-1 flex items-center pl-3 text-[11px] font-mono text-emerald-700 font-bold bg-emerald-50">
                Saved: 734 Bytes per MQTT transmission
              </div>
            </div>
            <div className="text-[11px] text-indigo-900/80 font-mono">
              Wire Layout: [Bytes 0..3: Big-Endian Float32 maxAbs] + [Bytes 4..249: 246 ×
              Signed Int8 in [-127, 127]]
            </div>
          </div>

          {/* Accuracy vs Compression Trade-off Table */}
          <div className="overflow-x-auto border border-slate-200 rounded-xl">
            <table className="w-full text-xs text-left border-collapse">
              <thead>
                <tr className="bg-slate-100 text-slate-700 border-b border-slate-200">
                  <th className="py-2 px-3 font-bold">Pipeline Metric</th>
                  <th className="py-2 px-3 font-bold text-right">32-Bit Baseline</th>
                  <th className="py-2 px-3 font-bold text-right">8-Bit Quantized</th>
                  <th className="py-2 px-3 font-bold text-right">Delta / Impact</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 font-mono">
                <tr>
                  <td className="py-2 px-3 font-sans font-medium text-slate-700">
                    MQTT Payload Size (per update)
                  </td>
                  <td className="py-2 px-3 text-right">984 B</td>
                  <td className="py-2 px-3 text-right font-bold text-indigo-700">
                    250 B
                  </td>
                  <td className="py-2 px-3 text-right font-bold text-emerald-700">
                    -734 B (-74.59%)
                  </td>
                </tr>
                <tr>
                  <td className="py-2 px-3 font-sans font-medium text-slate-700">
                    Traffic per FedAvg Round (2 up + 1 global)
                  </td>
                  <td className="py-2 px-3 text-right">2,952 B</td>
                  <td className="py-2 px-3 text-right font-bold text-indigo-700">
                    750 B
                  </td>
                  <td className="py-2 px-3 text-right font-bold text-emerald-700">
                    -2,202 B / round
                  </td>
                </tr>
                <tr>
                  <td className="py-2 px-3 font-sans font-medium text-slate-700">
                    Global Model Overall Accuracy (6 Poses)
                  </td>
                  <td className="py-2 px-3 text-right">
                    {global32BitAccuracy.toFixed(1)}%
                  </td>
                  <td className="py-2 px-3 text-right font-bold text-indigo-700">
                    {global8BitAccuracy.toFixed(1)}%
                  </td>
                  <td className="py-2 px-3 text-right text-amber-700">
                    -{accuracyDelta.toFixed(2)}% (negligible)
                  </td>
                </tr>
                <tr>
                  <td className="py-2 px-3 font-sans font-medium text-slate-700">
                    Global Model Macro-F1 Score
                  </td>
                  <td className="py-2 px-3 text-right">
                    {global32BitMacroF1.toFixed(1)}%
                  </td>
                  <td className="py-2 px-3 text-right font-bold text-indigo-700">
                    {global8BitMacroF1.toFixed(1)}%
                  </td>
                  <td className="py-2 px-3 text-right text-amber-700">
                    -{Math.abs(global32BitMacroF1 - global8BitMacroF1).toFixed(2)}%
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* Right 6 Cols: Cumulative Bandwidth Calculator & Live Quantization Codec Inspector */}
        <div className="lg:col-span-6 space-y-4">
          <div className="p-4 rounded-xl bg-slate-900 text-white space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h3 className="text-sm font-bold text-white">
                  Cumulative MQTT Bandwidth Efficiency Calculator
                </h3>
                <p className="text-xs text-slate-400">
                  {federatedStatus.completedRounds > 0
                    ? `Tracking ${federatedStatus.completedRounds} live completed FedAvg rounds from server`
                    : `Projecting bandwidth across ${effectiveRounds} federated training rounds`}
                </p>
              </div>
              {federatedStatus.completedRounds === 0 && (
                <div className="flex items-center gap-2 text-xs">
                  <label htmlFor="rounds-slider" className="text-slate-300 font-mono">
                    Rounds: {projectedRounds}
                  </label>
                  <input
                    id="rounds-slider"
                    type="range"
                    min={1}
                    max={100}
                    value={projectedRounds}
                    onChange={(e) => setProjectedRounds(Number(e.target.value))}
                    className="w-24 accent-indigo-400 cursor-pointer"
                  />
                </div>
              )}
            </div>

            <div className="grid grid-cols-3 gap-3 pt-1">
              <div className="p-2.5 rounded-lg bg-slate-800 border border-slate-700">
                <div className="text-[10px] uppercase text-slate-400 font-semibold">
                  32-Bit Total Traffic
                </div>
                <div className="text-lg font-extrabold font-mono text-slate-200">
                  {(totalBaselineBytes / 1024).toFixed(2)} KB
                </div>
                <div className="text-[10px] text-slate-400 font-mono">
                  {totalBaselineBytes.toLocaleString()} B
                </div>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-800 border border-indigo-500/50">
                <div className="text-[10px] uppercase text-indigo-300 font-semibold">
                  8-Bit Quantized Traffic
                </div>
                <div className="text-lg font-extrabold font-mono text-indigo-400">
                  {(totalQuantizedBytes / 1024).toFixed(2)} KB
                </div>
                <div className="text-[10px] text-slate-400 font-mono">
                  {totalQuantizedBytes.toLocaleString()} B
                </div>
              </div>
              <div className="p-2.5 rounded-lg bg-emerald-950/60 border border-emerald-500/50">
                <div className="text-[10px] uppercase text-emerald-300 font-semibold">
                  Total Bandwidth Saved
                </div>
                <div className="text-lg font-extrabold font-mono text-emerald-400">
                  {(totalSavedBytes / 1024).toFixed(2)} KB
                </div>
                <div className="text-[10px] text-emerald-300/80 font-mono">
                  {totalSavedBytes.toLocaleString()} B saved
                </div>
              </div>
            </div>
          </div>

          {/* Interactive Symmetric Min-Max Quantization Inspector */}
          <div className="border border-slate-200 rounded-xl p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                  Interactive Codec Inspector (Quantizer.kt ↔ QuantizedWeightsCodec.java)
                </h4>
                <p className="text-[11px] text-slate-500 font-mono mt-0.5">
                  maxAbs = {quantSim.maxAbs.toFixed(4)} | scale = 127 / maxAbs ={' '}
                  {quantSim.scale.toFixed(3)} | Mean Error:{' '}
                  {quantSim.meanAbsoluteError.toFixed(5)}
                </p>
              </div>
              <button
                type="button"
                onClick={randomizeSampleWeights}
                className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 text-xs font-semibold cursor-pointer"
              >
                ↻ Sample New MLP Weights
              </button>
            </div>

            <div className="overflow-x-auto max-h-48">
              <table className="w-full text-[11px] font-mono border-collapse text-left">
                <thead>
                  <tr className="bg-slate-50 border-y border-slate-200 text-slate-600">
                    <th className="py-1.5 px-2">Parameter</th>
                    <th className="py-1.5 px-2 text-right">Float32 (4B)</th>
                    <th className="py-1.5 px-2 text-right">Int8 Wire (1B)</th>
                    <th className="py-1.5 px-2 text-right">Dequantized</th>
                    <th className="py-1.5 px-2 text-right">|Error|</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {quantSim.steps.map((s) => (
                    <tr key={s.index}>
                      <td className="py-1 px-2 text-slate-700 truncate max-w-[160px]">
                        {s.layerLabel}
                      </td>
                      <td className="py-1 px-2 text-right text-slate-800">
                        {s.originalFloat32.toFixed(4)}
                      </td>
                      <td className="py-1 px-2 text-right font-bold text-indigo-700">
                        {s.quantizedInt8}
                      </td>
                      <td className="py-1 px-2 text-right text-teal-700">
                        {s.dequantizedFloat32.toFixed(4)}
                      </td>
                      <td className="py-1 px-2 text-right text-slate-500">
                        {s.absError.toFixed(5)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}


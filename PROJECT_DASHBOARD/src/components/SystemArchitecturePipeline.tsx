/**
 * ============================================================================
 * FILE README: src/components/SystemArchitecturePipeline.tsx
 * ============================================================================
 * Purpose:
 *   Visualizes the 5-stage Federated Learning workflow (`Collect Data` ->
 *   `Train Locally` -> `Send Update` -> `Aggregate` -> `Evaluate`), the
 *   Non-IID local training vs. universal evaluation dataset split, on-device
 *   feature extraction & Kotlin MLP hyperparameters, and Privacy Rule
 *   enforcement.
 *
 * Alignment with PROJECT_ANDROID & PROJECT_BACKEND:
 *   - Documents the 100ms sliding window multi-sensor feature extractor
 *     (`FeatureExtractor.kt`: 24 normalized features across Gravity, Accel & Gyro).
 *   - Documents the 24 -> 16 -> 6 Kotlin MLP (`MultiLayerPerceptron.kt`:
 *     Sigmoid hidden, Softmax output, learning rate 0.1, 502 parameters).
 *   - Documents the Non-IID training distribution (50 samples/pose on 3 poses
 *     per client) and universal calibration dataset (10 samples/pose across all
 *     6 poses per phone).
 * ============================================================================
 */
import {
  MLP_HIDDEN_NEURONS,
  MLP_INPUT_FEATURES,
  MLP_LEARNING_RATE,
  MLP_OUTPUT_CLASSES,
  MLP_TOTAL_WEIGHTS,
  POSE_DEFINITIONS,
} from '../types/federated';

interface SystemArchitecturePipelineProps {
  localModelsTrained?: boolean;
  lastResetMode?: 'NONE' | 'RESET_GLOBAL' | 'RESET_ALL';
}

export default function SystemArchitecturePipeline({
  localModelsTrained = true,
  lastResetMode = 'NONE',
}: SystemArchitecturePipelineProps) {
  const isResetAll = !localModelsTrained || lastResetMode === 'RESET_ALL';

  const workflowSteps = [
    {
      step: '1. Collect Data',
      tier: 'Edge Tier (Android)',
      detail:
        'SensorService samples 3-axis Gravity, Accelerometer & Gyroscope over a 100ms sliding window; raw sensor data stays strictly on-device.',
    },
    {
      step: '2. Train Locally',
      tier: 'Edge Tier (Kotlin MLP)',
      detail:
        '24 normalized sensor features (8 Gravity + 8 Accel + 8 Gyro) train a 24→16→6 MLP (η=0.1, 502 weights) on non-IID local poses.',
    },
    {
      step: '3. Send Update',
      tier: 'MQTT Binary Publish',
      detail:
        'Quantizer.kt symmetrically compresses 502 float32 weights (2,008B) into 506B (4B maxAbs + 502 int8) on federated/local/weights/{clientId}.',
    },
    {
      step: '4. Aggregate (FedAvg)',
      tier: 'Spring Boot Server',
      detail:
        'FederatedAggregationService dequantizes client updates, computes weighted FedAvg across clients, persists snapshot, and broadcasts global weights.',
    },
    {
      step: '5. Evaluate',
      tier: 'Edge & React Dashboard',
      detail:
        'Compare Client A Local, Client B Local, and Federated Global models across all 6 spatial poses via live WebSockets and calibration benchmarks.',
    },
  ];

  return (
    <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-2 border-b border-slate-200 pb-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900">
            Federated Learning Pipeline, Non-IID Data Design &amp; Privacy Compliance
          </h2>
          <p className="text-sm text-slate-600 mt-0.5">
            End-to-end architecture across Android Edge Clients, Eclipse Mosquitto MQTT,
            Spring Boot FedAvg Server, and React WebSockets Dashboard.
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-200 self-start md:self-auto">
          🔒 Privacy Rule Enforced: 0 Raw Sensor Samples Transmitted
        </span>
      </div>

      {/* 5-Stage Federated Learning Workflow */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
        {workflowSteps.map((item) => (
          <div
            key={item.step}
            className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col justify-between space-y-2"
          >
            <div>
              <div className="text-[10px] font-mono font-bold uppercase text-indigo-600">
                {item.tier}
              </div>
              <div className="text-sm font-bold text-slate-900 mt-0.5">
                {item.step}
              </div>
              <p className="text-xs text-slate-600 mt-1.5 leading-relaxed">
                {item.detail}
              </p>
            </div>
          </div>
        ))}
      </div>

      {/* Non-IID Data Split & Pose Specification Table + On-Device MLP Specs */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-8 overflow-x-auto border border-slate-200 rounded-xl">
          <table className="w-full text-xs text-left border-collapse">
            <thead>
              <tr className="bg-slate-100 text-slate-700 border-b border-slate-200">
                <th className="py-2.5 px-3 font-bold">Pose Class</th>
                <th className="py-2.5 px-3 font-bold">Physical Orientation Definition</th>
                <th className="py-2.5 px-3 font-bold text-center">
                  Client A Train
                </th>
                <th className="py-2.5 px-3 font-bold text-center">
                  Client B Train
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {POSE_DEFINITIONS.map((pose) => {
                const isA = pose.primaryClient === 'CLIENT_A';
                return (
                  <tr key={pose.name} className="hover:bg-slate-50">
                    <td className="py-2 px-3 font-bold text-slate-900">
                      {pose.name}
                    </td>
                    <td className="py-2 px-3 text-slate-600">{pose.definition}</td>
                    <td className="py-2 px-3 text-center font-mono">
                      {isResetAll ? (
                        <span className="px-2 py-0.5 rounded bg-rose-100 text-rose-800 font-bold">
                          0/50 (reset)
                        </span>
                      ) : isA ? (
                        <span className="px-2 py-0.5 rounded bg-blue-100 text-blue-800 font-bold">
                          ✓ (50/50)
                        </span>
                      ) : (
                        <span className="text-slate-400">0/50 (unseen)</span>
                      )}
                    </td>
                    <td className="py-2 px-3 text-center font-mono">
                      {isResetAll ? (
                        <span className="px-2 py-0.5 rounded bg-rose-100 text-rose-800 font-bold">
                          0/50 (reset)
                        </span>
                      ) : !isA ? (
                        <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 font-bold">
                          ✓ (50/50)
                        </span>
                      ) : (
                        <span className="text-slate-400">0/50 (unseen)</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Right 4 Cols: On-Device Feature & Model Engineering Summary */}
        <div className="lg:col-span-4 bg-slate-900 text-slate-100 rounded-xl p-4 space-y-3 flex flex-col justify-between">
          <div className="space-y-2.5">
            <h3 className="text-xs font-bold uppercase tracking-wider text-indigo-300">
              On-Device Local Learning Specification
            </h3>
            <div className="space-y-1.5 text-xs font-mono">
              <div className="flex justify-between border-b border-slate-800 pb-1">
                <span className="text-slate-400">Sliding Window:</span>
                <span>100 ms (Grav + Accel + Gyro)</span>
              </div>
              <div className="flex justify-between border-b border-slate-800 pb-1">
                <span className="text-slate-400">Feature Vector:</span>
                <span>{MLP_INPUT_FEATURES} floats (3 × 8 stats)</span>
              </div>
              <div className="flex justify-between border-b border-slate-800 pb-1">
                <span className="text-slate-400">MLP Architecture:</span>
                <span>
                  {MLP_INPUT_FEATURES} → {MLP_HIDDEN_NEURONS} (Sigmoid) →{' '}
                  {MLP_OUTPUT_CLASSES} (Softmax)
                </span>
              </div>
              <div className="flex justify-between border-b border-slate-800 pb-1">
                <span className="text-slate-400">Total Parameters:</span>
                <span>{MLP_TOTAL_WEIGHTS} weights &amp; biases</span>
              </div>
              <div className="flex justify-between border-b border-slate-800 pb-1">
                <span className="text-slate-400">Optimizer &amp; Rate:</span>
                <span>SGD Cross-Entropy (η = {MLP_LEARNING_RATE})</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Update Trigger:</span>
                <span>Every 10 samples + 20 epochs</span>
              </div>
            </div>
          </div>

          <div className="p-2.5 rounded-lg bg-slate-800/90 border border-slate-700 text-[11px] text-slate-300">
            <strong className="text-emerald-400">Why Federated Learning Wins:</strong>{' '}
            Isolated Client A &amp; B models never see the complementary 3 poses (0%
            recall on unseen classes). FedAvg combines both local weight updates into a
            single global model without sharing raw sensor readings.
          </div>
        </div>
      </div>
    </section>
  );
}


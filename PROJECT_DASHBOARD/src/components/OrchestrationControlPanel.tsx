/**
 * ============================================================================
 * FILE README: src/components/OrchestrationControlPanel.tsx
 * ============================================================================
 * Purpose:
 *   Operator remote-control panel and live Federated Averaging (FedAvg) round
 *   monitor integrating with the Spring Boot backend (`MqttController.java` &
 *   `FederatedAggregationService.java`) and Android edge clients
 *   (`MqttClientManager.kt`).
 *
 * Key Responsibilities:
 *   1. Experiment Configuration: Dispatches `START_TRAINING` with
 *      `use_quantization: false` (32-bit baseline, 984 B) or
 *      `use_quantization: true` (8-bit quantized, 250 B) via
 *      `POST /api/mqtt/federated/training`.
 *   2. Active Inference Model Toggle: Dispatches `SET_MODEL` (`LOCAL` vs.
 *      `GLOBAL`) via `POST /api/mqtt/federated/model/toggle`.
 *   3. Snapshot & Reset Controls: Triggers server-backed model republishing
 *      (`POST /api/mqtt/federated/model/republish` and `/republish-all`) and
 *      edge model resets (`RESET_GLOBAL`, `RESET_ALL`).
 *   4. FedAvg Aggregation Monitor: Displays `completedRounds`,
 *      `receivedUpdates`, `pendingUpdates / expectedClients`, parameter count
 *      (246 floats), latest MQTT topic, and the orchestration event feed.
 * ============================================================================
 */
import {
  MLP_TOTAL_WEIGHTS,
  type ActiveModelType,
  type ActivityLogEntry,
  type FederatedStatus,
  type ReceivedMqttMessage,
} from '../types/federated';

interface OrchestrationControlPanelProps {
  federatedStatus: FederatedStatus;
  latestMqttMessage: ReceivedMqttMessage | null;
  activeModel: ActiveModelType;
  selectedPipeline: '32BIT' | '8BIT';
  isDispatching: boolean;
  lastActionFeedback: string | null;
  activityLog: ActivityLogEntry[];
  onStartTraining: (useQuantization: boolean) => void;
  onToggleModel: (model: ActiveModelType) => void;
  onRepublishGlobal: () => void;
  onRepublishAll: () => void;
  onSendResetCommand: (resetType: 'RESET_GLOBAL' | 'RESET_ALL') => void;
  onRefreshStatus: () => void;
  onSimulateRoundStep: () => void;
}

export default function OrchestrationControlPanel({
  federatedStatus,
  latestMqttMessage,
  activeModel,
  selectedPipeline,
  isDispatching,
  lastActionFeedback,
  activityLog,
  onStartTraining,
  onToggleModel,
  onRepublishGlobal,
  onRepublishAll,
  onSendResetCommand,
  onRefreshStatus,
  onSimulateRoundStep,
}: OrchestrationControlPanelProps) {
  const pendingProgress =
    federatedStatus.expectedClients > 0
      ? Math.min(
          100,
          Math.round(
            (federatedStatus.pendingUpdates / federatedStatus.expectedClients) * 100
          )
        )
      : 0;

  return (
    <section className="grid grid-cols-1 lg:grid-cols-12 gap-6">
      {/* Left 7 Cols: Interactive MQTT Command & Experiment Control Panel */}
      <div className="lg:col-span-7 bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-3">
          <div>
            <h2 className="text-xl font-bold text-slate-900">
              Federated Orchestration & Remote Edge Controls
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Dispatches JSON control payloads over Spring Boot REST → Mosquitto MQTT{' '}
              <code className="bg-slate-100 px-1.5 py-0.5 rounded font-mono">
                federated/commands
              </code>
            </p>
          </div>
          {lastActionFeedback && (
            <span className="text-xs font-medium px-2.5 py-1 rounded-md bg-indigo-50 text-indigo-700 border border-indigo-200">
              {lastActionFeedback}
            </span>
          )}
        </div>

        {/* 1. Experiment Configuration (Comparative Training Runs) */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-700">
              1. Experiment Configuration (Comparative Training Runs)
            </h3>
            <span className="text-xs font-mono text-slate-500">
              Active Pipeline:{' '}
              <strong className="text-indigo-700">
                {selectedPipeline === '8BIT'
                  ? '8-bit Quantized (250 B)'
                  : '32-bit Baseline (984 B)'}
              </strong>
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <button
              type="button"
              disabled={isDispatching}
              onClick={() => onStartTraining(false)}
              className={`p-3.5 rounded-xl border text-left transition-all cursor-pointer flex flex-col justify-between ${
                selectedPipeline === '32BIT'
                  ? 'bg-blue-600 text-white border-blue-700 shadow-sm ring-2 ring-blue-300'
                  : 'bg-blue-50/70 hover:bg-blue-100/80 text-blue-950 border-blue-200'
              }`}
            >
              <div className="flex items-center justify-between w-full">
                <span className="font-bold text-sm">
                  Start Training (32-bit Baseline)
                </span>
                <span
                  className={`text-[10px] font-mono px-2 py-0.5 rounded ${
                    selectedPipeline === '32BIT'
                      ? 'bg-blue-800 text-blue-100'
                      : 'bg-blue-200/80 text-blue-900'
                  }`}
                >
                  984 Bytes
                </span>
              </div>
              <code
                className={`text-[11px] font-mono mt-2 block truncate ${
                  selectedPipeline === '32BIT' ? 'text-blue-100' : 'text-blue-700'
                }`}
              >
                {`{"command":"START_TRAINING","use_quantization":false}`}
              </code>
            </button>

            <button
              type="button"
              disabled={isDispatching}
              onClick={() => onStartTraining(true)}
              className={`p-3.5 rounded-xl border text-left transition-all cursor-pointer flex flex-col justify-between ${
                selectedPipeline === '8BIT'
                  ? 'bg-indigo-600 text-white border-indigo-700 shadow-sm ring-2 ring-indigo-300'
                  : 'bg-indigo-50/70 hover:bg-indigo-100/80 text-indigo-950 border-indigo-200'
              }`}
            >
              <div className="flex items-center justify-between w-full">
                <span className="font-bold text-sm">
                  Start Training (8-bit Quantized)
                </span>
                <span
                  className={`text-[10px] font-mono px-2 py-0.5 rounded ${
                    selectedPipeline === '8BIT'
                      ? 'bg-indigo-800 text-indigo-100'
                      : 'bg-indigo-200/80 text-indigo-900'
                  }`}
                >
                  250 Bytes (-74.6%)
                </span>
              </div>
              <code
                className={`text-[11px] font-mono mt-2 block truncate ${
                  selectedPipeline === '8BIT'
                    ? 'text-indigo-100'
                    : 'text-indigo-700'
                }`}
              >
                {`{"command":"START_TRAINING","use_quantization":true}`}
              </code>
            </button>
          </div>
          <p className="text-xs text-slate-500">
            Initiates comparative training runs via{' '}
            <code className="font-mono">/api/mqtt/federated/training</code>, resets edge
            models, and routes training updates through the selected pipeline.
          </p>
        </div>

        {/* 2. Active Classification Model Toggle (Live Evaluation) */}
        <div className="space-y-3 pt-2 border-t border-slate-100">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-700">
              2. Active Classification Model (Live Inference Toggle)
            </h3>
            <span className="text-xs font-mono text-slate-500">
              Current Mode:{' '}
              <strong
                className={
                  activeModel === 'GLOBAL' ? 'text-teal-700' : 'text-slate-800'
                }
              >
                {activeModel}
              </strong>
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <button
              type="button"
              disabled={isDispatching}
              onClick={() => onToggleModel('LOCAL')}
              className={`p-3.5 rounded-xl border text-left transition-all cursor-pointer ${
                activeModel === 'LOCAL'
                  ? 'bg-slate-800 text-white border-slate-900 shadow-sm ring-2 ring-slate-300'
                  : 'bg-slate-50 hover:bg-slate-100 text-slate-800 border-slate-200'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm">Switch to Local Model</span>
                <span
                  className={`text-[10px] font-mono px-2 py-0.5 rounded ${
                    activeModel === 'LOCAL'
                      ? 'bg-slate-700 text-slate-100'
                      : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  Isolated Non-IID Baseline
                </span>
              </div>
              <code
                className={`text-[11px] font-mono mt-2 block truncate ${
                  activeModel === 'LOCAL' ? 'text-slate-300' : 'text-slate-500'
                }`}
              >
                {`{"command":"SET_MODEL","active_model":"LOCAL"}`}
              </code>
            </button>

            <button
              type="button"
              disabled={isDispatching}
              onClick={() => onToggleModel('GLOBAL')}
              className={`p-3.5 rounded-xl border text-left transition-all cursor-pointer ${
                activeModel === 'GLOBAL'
                  ? 'bg-teal-600 text-white border-teal-700 shadow-sm ring-2 ring-teal-300'
                  : 'bg-teal-50/70 hover:bg-teal-100/80 text-teal-950 border-teal-200'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm">Switch to Global Model</span>
                <span
                  className={`text-[10px] font-mono px-2 py-0.5 rounded ${
                    activeModel === 'GLOBAL'
                      ? 'bg-teal-800 text-teal-100'
                      : 'bg-teal-200 text-teal-900'
                  }`}
                >
                  Collaboratively Aggregated
                </span>
              </div>
              <code
                className={`text-[11px] font-mono mt-2 block truncate ${
                  activeModel === 'GLOBAL' ? 'text-teal-100' : 'text-teal-700'
                }`}
              >
                {`{"command":"SET_MODEL","active_model":"GLOBAL"}`}
              </code>
            </button>
          </div>
        </div>

        {/* 3. Model Snapshot Persistence & Reset Operations */}
        <div className="space-y-2.5 pt-2 border-t border-slate-100">
          <h3 className="text-sm font-bold uppercase tracking-wider text-slate-700">
            3. Server Snapshot Synchronization & Edge Model Reset
          </h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <button
              type="button"
              disabled={isDispatching}
              onClick={onRepublishGlobal}
              className="px-3 py-2 rounded-lg text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-800 border border-slate-300 transition-colors cursor-pointer"
              title="POST /api/mqtt/federated/model/republish — Republishes latest global weights to federated/global/weights"
            >
              ↻ Republish Global
            </button>
            <button
              type="button"
              disabled={isDispatching}
              onClick={onRepublishAll}
              className="px-3 py-2 rounded-lg text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-800 border border-slate-300 transition-colors cursor-pointer"
              title="POST /api/mqtt/federated/model/republish-all — Republishes global + per-client local models from PostgreSQL snapshot repository"
            >
              ⇄ Sync All Models
            </button>
            <button
              type="button"
              disabled={isDispatching}
              onClick={() => onSendResetCommand('RESET_GLOBAL')}
              className="px-3 py-2 rounded-lg text-xs font-semibold bg-amber-50 hover:bg-amber-100 text-amber-900 border border-amber-300 transition-colors cursor-pointer"
              title='Publishes {"command":"RESET_GLOBAL"} to federated/commands'
            >
              ⟲ Reset Global
            </button>
            <button
              type="button"
              disabled={isDispatching}
              onClick={() => onSendResetCommand('RESET_ALL')}
              className="px-3 py-2 rounded-lg text-xs font-semibold bg-rose-50 hover:bg-rose-100 text-rose-900 border border-rose-300 transition-colors cursor-pointer"
              title='Publishes {"command":"RESET_ALL"} to federated/commands'
            >
              ✕ Reset All Edge
            </button>
          </div>
        </div>
      </div>

      {/* Right 5 Cols: Live Federated Aggregation Status & Round Monitor */}
      <div className="lg:col-span-5 bg-white rounded-xl shadow-sm border border-slate-200 p-6 flex flex-col justify-between space-y-5">
        <div>
          <div className="flex items-center justify-between border-b border-slate-200 pb-3">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                FedAvg Aggregation Status
              </h2>
              <p className="text-xs text-slate-500">
                Spring Boot <code className="font-mono">/api/mqtt/federated/status</code>
              </p>
            </div>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={onSimulateRoundStep}
                className="text-xs px-2.5 py-1 rounded bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 font-medium cursor-pointer"
                title="Simulate an incoming client weight update / FedAvg aggregation round for demonstration"
              >
                + Sim Update
              </button>
              <button
                type="button"
                onClick={onRefreshStatus}
                className="text-xs px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 font-medium cursor-pointer"
              >
                Refresh
              </button>
            </div>
          </div>

          {/* 4 KPI Stat Cards */}
          <div className="grid grid-cols-2 gap-3 mt-4">
            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[11px] font-semibold uppercase text-slate-500">
                Completed FedAvg Rounds
              </div>
              <div className="text-2xl font-extrabold text-indigo-600 mt-0.5">
                {federatedStatus.completedRounds}
              </div>
              <div className="text-[11px] text-slate-400">
                Global broadcasts to edge
              </div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[11px] font-semibold uppercase text-slate-500">
                Client Weight Updates
              </div>
              <div className="text-2xl font-extrabold text-blue-600 mt-0.5">
                {federatedStatus.receivedUpdates}
              </div>
              <div className="text-[11px] text-slate-400">
                Every 10 local samples trained
              </div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[11px] font-semibold uppercase text-slate-500">
                Current Round Barrier
              </div>
              <div className="text-lg font-bold text-slate-800 mt-0.5">
                {federatedStatus.pendingUpdates} / {federatedStatus.expectedClients}{' '}
                <span className="text-xs font-normal text-slate-500">clients</span>
              </div>
              <div className="w-full h-2 bg-slate-200 rounded-full overflow-hidden mt-1.5">
                <div
                  className="h-full bg-emerald-500 transition-all duration-300"
                  style={{ width: `${pendingProgress}%` }}
                />
              </div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[11px] font-semibold uppercase text-slate-500">
                MLP Model Parameters
              </div>
              <div className="text-lg font-bold text-slate-800 mt-0.5">
                {federatedStatus.globalWeightCount > 0
                  ? federatedStatus.globalWeightCount
                  : MLP_TOTAL_WEIGHTS}{' '}
                <span className="text-xs font-normal text-slate-500">floats</span>
              </div>
              <div className="text-[11px] text-slate-400">
                8 → 16 → 6 Kotlin MLP
              </div>
            </div>
          </div>

          {/* Last Aggregation & Latest MQTT Message */}
          <div className="mt-4 p-3 rounded-lg bg-slate-900 text-slate-200 text-xs font-mono space-y-1.5">
            <div className="flex justify-between">
              <span className="text-slate-400">Last Global Aggregation:</span>
              <span className="text-emerald-400">
                {federatedStatus.lastAggregationAt
                  ? new Date(federatedStatus.lastAggregationAt).toLocaleTimeString()
                  : 'Pending first round'}
              </span>
            </div>
            <div className="flex justify-between gap-2">
              <span className="text-slate-400 shrink-0">Latest MQTT Topic:</span>
              <span className="text-cyan-300 truncate">
                {latestMqttMessage?.topic || 'federated/telemetry/#'}
              </span>
            </div>
          </div>
        </div>

        {/* Live Orchestration & Round Event Feed */}
        <div className="space-y-1.5">
          <div className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Recent Orchestration Events
          </div>
          <div className="max-h-36 overflow-y-auto space-y-1.5 pr-1">
            {activityLog.length === 0 ? (
              <div className="text-xs text-slate-400 italic py-2">
                No orchestration events logged yet.
              </div>
            ) : (
              activityLog.slice(0, 8).map((item) => (
                <div
                  key={item.id}
                  className="text-xs bg-slate-50 border border-slate-200 rounded px-2.5 py-1.5 flex items-start justify-between gap-2"
                >
                  <div>
                    <span
                      className={`inline-block text-[10px] font-mono font-bold px-1.5 py-0.2 rounded mr-1.5 ${
                        item.category === 'AGGREGATION'
                          ? 'bg-emerald-100 text-emerald-800'
                          : item.category === 'COMMAND'
                          ? 'bg-indigo-100 text-indigo-800'
                          : 'bg-slate-200 text-slate-700'
                      }`}
                    >
                      {item.category}
                    </span>
                    <span className="text-slate-800 font-medium">
                      {item.summary}
                    </span>
                  </div>
                  <span className="text-[10px] font-mono text-slate-400 shrink-0">
                    {item.timestamp}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </section>
  );
}


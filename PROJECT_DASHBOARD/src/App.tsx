/**
 * ============================================================================
 * FILE README: src/App.tsx
 * ============================================================================
 * Purpose:
 *   Root container component for the CSC 8223 Federated Learning Smartphone
 *   Pose Dashboard (`PROJECT_DASHBOARD`).
 *
 * Core Responsibilities:
 *   1. WebSocket Telemetry Client (`/ws/telemetry`):
 *      Maintains a resilient, auto-reconnecting WebSocket connection to the
 *      Spring Boot backend (`TelemetryWebSocketHandler.java`) to receive live
 *      pose predictions (`clientId`, `pose`, `timestamp`) from Android clients.
 *   2. Federated Orchestration Polling & Command Dispatch (`/api/mqtt/...`):
 *      Polls `/api/mqtt/federated/status` and `/api/mqtt/latest` every 2.5s
 *      and dispatches MQTT control commands (`START_TRAINING` with 32-bit or
 *      8-bit quantization, `SET_MODEL` for `LOCAL` vs `GLOBAL`, model snapshot
 *      republishing, and `RESET_GLOBAL` / `RESET_ALL`).
 *   3. State Coordination Across Dashboard Views:
 *      Coordinates state across `<PhonePoseVisualizer />`,
 *      `<OrchestrationControlPanel />`, `<ModelComparisonSection />`,
 *      `<QuantizationContributionSection />`, and `<SystemArchitecturePipeline />`.
 * ============================================================================
 */
import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
  MLP_TOTAL_WEIGHTS,
  POSE_NAMES,
  type ActiveModelType,
  type ActivityLogEntry,
  type ClientRole,
  type ConfusionMatrix6x6,
  type FederatedStatus,
  type LatestMessageResponse,
  type PoseName,
  type ReceivedMqttMessage,
  type TelemetryMessage,
} from './types/federated';
import {
  REFERENCE_CLIENT_A_MATRIX,
  REFERENCE_CLIENT_B_MATRIX,
  REFERENCE_GLOBAL_32BIT_MATRIX,
  REFERENCE_GLOBAL_8BIT_MATRIX,
  cloneConfusionMatrix,
  computeModelEvaluation,
} from './data/evaluationData';
import PhonePoseVisualizer from './components/PhonePoseVisualizer';
import OrchestrationControlPanel from './components/OrchestrationControlPanel';
import ModelComparisonSection from './components/ModelComparisonSection';
import QuantizationContributionSection from './components/QuantizationContributionSection';
import SystemArchitecturePipeline from './components/SystemArchitecturePipeline';

type DashboardTab = 'ALL' | 'LIVE_DEMO' | 'EVALUATION' | 'QUANTIZATION' | 'ARCHITECTURE';

export default function App() {
  // Telemetry & WebSocket state
  const [telemetry, setTelemetry] = useState<Record<string, TelemetryMessage>>({});
  const [telemetryHistory, setTelemetryHistory] = useState<
    Record<string, { pose: string; timestamp: number }[]>
  >({});
  const [packetCounts, setPacketCounts] = useState<Record<string, number>>({});
  const [wsConnected, setWsConnected] = useState(false);
  const [backendReachable, setBackendReachable] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);

  // Client A & Client B role mapping (auto-binds incoming EdgeClient_<id> devices)
  const [clientRoleMapping, setClientRoleMapping] = useState<
    Record<ClientRole, string>
  >({
    CLIENT_A: 'EdgeClient_A',
    CLIENT_B: 'EdgeClient_B',
  });

  // Federated Orchestration & Active Model state
  const [federatedStatus, setFederatedStatus] = useState<FederatedStatus>({
    receivedUpdates: 0,
    completedRounds: 0,
    pendingUpdates: 0,
    expectedClients: 2,
    globalWeightCount: MLP_TOTAL_WEIGHTS,
    lastAggregationAt: null,
  });
  const [latestMqttMessage, setLatestMqttMessage] =
    useState<ReceivedMqttMessage | null>(null);
  const [activeModel, setActiveModel] = useState<ActiveModelType>('GLOBAL');
  const [selectedPipeline, setSelectedPipeline] = useState<'32BIT' | '8BIT'>('8BIT');
  const [isDispatching, setIsDispatching] = useState(false);
  const [lastActionFeedback, setLastActionFeedback] = useState<string | null>(null);
  const [activityLog, setActivityLog] = useState<ActivityLogEntry[]>([]);

  // Demo Pose Simulator state
  const [simulationEnabled, setSimulationEnabled] = useState(false);

  // Evaluation Matrices state (Reference vs Live Interactive)
  const [evaluationMode, setEvaluationMode] = useState<'REFERENCE' | 'LIVE'>(
    'REFERENCE'
  );
  const [liveClientAMatrix, setLiveClientAMatrix] = useState<ConfusionMatrix6x6>(() =>
    cloneConfusionMatrix(REFERENCE_CLIENT_A_MATRIX)
  );
  const [liveClientBMatrix, setLiveClientBMatrix] = useState<ConfusionMatrix6x6>(() =>
    cloneConfusionMatrix(REFERENCE_CLIENT_B_MATRIX)
  );
  const [liveGlobal8BitMatrix, setLiveGlobal8BitMatrix] =
    useState<ConfusionMatrix6x6>(() =>
      cloneConfusionMatrix(REFERENCE_GLOBAL_8BIT_MATRIX)
    );

  // Active dashboard view filter
  const [activeTab, setActiveTab] = useState<DashboardTab>('ALL');

  const appendLog = useCallback(
    (category: ActivityLogEntry['category'], summary: string, detail?: string) => {
      setActivityLog((prev) => [
        {
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
          timestamp: new Date().toLocaleTimeString(),
          category,
          summary,
          detail,
        },
        ...prev.slice(0, 29),
      ]);
    },
    []
  );

  // Handle incoming telemetry payload (from WebSocket or Demo Simulator)
  const ingestTelemetry = useCallback((data: TelemetryMessage) => {
    if (!data.clientId) return;

    setTelemetry((prev) => ({
      ...prev,
      [data.clientId]: data,
    }));

    setPacketCounts((prev) => ({
      ...prev,
      [data.clientId]: (prev[data.clientId] || 0) + 1,
    }));

    setTelemetryHistory((prev) => {
      const existing = prev[data.clientId] || [];
      if (existing[0]?.pose === data.pose) {
        return prev;
      }
      return {
        ...prev,
        [data.clientId]: [
          { pose: data.pose, timestamp: data.timestamp },
          ...existing.slice(0, 7),
        ],
      };
    });

    // Auto-map first two distinct EdgeClient_* IDs to CLIENT_A and CLIENT_B if defaults have no data
    setClientRoleMapping((prev) => {
      if (
        prev.CLIENT_A === data.clientId ||
        prev.CLIENT_B === data.clientId
      ) {
        return prev;
      }
      if (prev.CLIENT_A === 'EdgeClient_A' && data.clientId !== 'EdgeClient_B') {
        return { ...prev, CLIENT_A: data.clientId };
      }
      if (prev.CLIENT_B === 'EdgeClient_B' && data.clientId !== prev.CLIENT_A) {
        return { ...prev, CLIENT_B: data.clientId };
      }
      return prev;
    });
  }, []);

  // Connect to Spring Boot WebSocket /ws/telemetry
  useEffect(() => {
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let unmounted = false;
    let useDirectPortFallback = false;

    const connect = () => {
      if (unmounted) return;
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = useDirectPortFallback
        ? 'ws://localhost:8080/ws/telemetry'
        : `${protocol}//${window.location.host}/ws/telemetry`;

      try {
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
          if (unmounted) return;
          setWsConnected(true);
          setBackendReachable(true);
        };

        ws.onclose = () => {
          if (unmounted) return;
          setWsConnected(false);
          useDirectPortFallback = !useDirectPortFallback;
          reconnectTimer = setTimeout(connect, 3000);
        };

        ws.onerror = () => {
          ws.close();
        };

        ws.onmessage = (event) => {
          try {
            const data: TelemetryMessage = JSON.parse(event.data);
            if (data && data.clientId) {
              ingestTelemetry({
                clientId: data.clientId,
                pose: data.pose || 'Unknown',
                timestamp: data.timestamp || Date.now(),
                activeModel: data.activeModel,
                confidence: data.confidence,
              });
            }
          } catch (e) {
            console.error('Invalid telemetry JSON', e);
          }
        };
      } catch {
        reconnectTimer = setTimeout(connect, 3000);
      }
    };

    connect();

    return () => {
      unmounted = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.close();
      }
    };
  }, [ingestTelemetry]);

  // Poll Spring Boot /api/mqtt/federated/status and /api/mqtt/latest
  const prevCompletedRoundsRef = useRef<number>(0);

  const fetchBackendStatus = useCallback(async () => {
    try {
      const [statusRes, latestRes] = await Promise.all([
        fetch('/api/mqtt/federated/status'),
        fetch('/api/mqtt/latest'),
      ]);

      if (statusRes.ok) {
        setBackendReachable(true);
        const statusData: FederatedStatus = await statusRes.json();
        setFederatedStatus(statusData);

        if (statusData.completedRounds > prevCompletedRoundsRef.current) {
          prevCompletedRoundsRef.current = statusData.completedRounds;
          appendLog(
            'AGGREGATION',
            `FedAvg Round #${statusData.completedRounds} aggregated & broadcast to federated/global/weights`
          );
        }
      }

      if (latestRes.ok) {
        const latestData: LatestMessageResponse = await latestRes.json();
        if (latestData.message) {
          setLatestMqttMessage(latestData.message);
        }
      }
    } catch {
      // Backend offline or unreachable
    }
  }, [appendLog]);

  useEffect(() => {
    const initial = setTimeout(fetchBackendStatus, 0);
    const interval = setInterval(fetchBackendStatus, 2500);
    return () => {
      clearTimeout(initial);
      clearInterval(interval);
    };
  }, [fetchBackendStatus]);

  // Optional Demo Telemetry Simulator (cycles poses when enabled)
  useEffect(() => {
    if (!simulationEnabled) return;

    const clientAPoses: PoseName[] = [
      'Screen Up',
      'Screen Down',
      'Portrait Up',
      'Portrait Down',
      'Landscape Left',
      'Landscape Right',
    ];
    let step = 0;

    const timer = setInterval(() => {
      const poseA = clientAPoses[step % clientAPoses.length];
      const poseB = clientAPoses[(step + 3) % clientAPoses.length];
      ingestTelemetry({
        clientId: clientRoleMapping.CLIENT_A,
        pose: poseA,
        timestamp: Date.now(),
      });
      ingestTelemetry({
        clientId: clientRoleMapping.CLIENT_B,
        pose: poseB,
        timestamp: Date.now(),
      });
      step++;
    }, 2200);

    return () => clearInterval(timer);
  }, [simulationEnabled, clientRoleMapping, ingestTelemetry]);

  // Remote MQTT Command Handlers (integrating MqttController.java endpoints)
  const handleStartTraining = async (useQuantization: boolean) => {
    setIsDispatching(true);
    const pipelineLabel = useQuantization
      ? '8-bit Quantized (250B)'
      : '32-bit Baseline (984B)';
    setSelectedPipeline(useQuantization ? '8BIT' : '32BIT');

    try {
      const res = await fetch('/api/mqtt/federated/training', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ useQuantization }),
      });

      if (!res.ok) {
        // Fallback to generic command endpoint
        await fetch('/api/mqtt/federated/command', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            command: 'START_TRAINING',
            use_quantization: useQuantization,
          }),
        });
      }

      const msg = `Queued START_TRAINING (${pipelineLabel})`;
      setLastActionFeedback(msg);
      appendLog(
        'COMMAND',
        `Dispatched START_TRAINING [use_quantization=${useQuantization}]`
      );
      await fetchBackendStatus();
    } catch {
      const msg = `Prepared START_TRAINING (${pipelineLabel}) [Offline Demo Mode]`;
      setLastActionFeedback(msg);
      appendLog(
        'COMMAND',
        `START_TRAINING [use_quantization=${useQuantization}] (Local UI State)`
      );
    } finally {
      setIsDispatching(false);
    }
  };

  const handleToggleModel = async (model: ActiveModelType) => {
    setIsDispatching(true);
    setActiveModel(model);

    try {
      const res = await fetch('/api/mqtt/federated/model/toggle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ activeModel: model }),
      });

      if (!res.ok) {
        await fetch('/api/mqtt/federated/command', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            command: 'SET_MODEL',
            active_model: model,
          }),
        });
      }

      setLastActionFeedback(`Switched edge inference to ${model} model`);
      appendLog('COMMAND', `Dispatched SET_MODEL [active_model="${model}"]`);
      await fetchBackendStatus();
    } catch {
      setLastActionFeedback(`Active model set to ${model} [Offline Demo Mode]`);
      appendLog('COMMAND', `SET_MODEL [active_model="${model}"] (Local UI State)`);
    } finally {
      setIsDispatching(false);
    }
  };

  const handleRepublishGlobal = async () => {
    setIsDispatching(true);
    try {
      await fetch('/api/mqtt/federated/model/republish', { method: 'POST' });
      setLastActionFeedback('Republished global weights to broker');
      appendLog(
        'AGGREGATION',
        'Republished persisted global weights to federated/global/weights'
      );
      await fetchBackendStatus();
    } catch {
      setLastActionFeedback('Republish global requested');
      appendLog('AGGREGATION', 'Requested global model republish');
    } finally {
      setIsDispatching(false);
    }
  };

  const handleRepublishAll = async () => {
    setIsDispatching(true);
    try {
      await fetch('/api/mqtt/federated/model/republish-all', { method: 'POST' });
      setLastActionFeedback('Synchronized global + per-client local models');
      appendLog(
        'AGGREGATION',
        'Republished global & per-client local weights from snapshot store'
      );
      await fetchBackendStatus();
    } catch {
      setLastActionFeedback('Sync all models requested');
      appendLog('AGGREGATION', 'Requested global + local model sync');
    } finally {
      setIsDispatching(false);
    }
  };

  const handleSendResetCommand = async (resetType: 'RESET_GLOBAL' | 'RESET_ALL') => {
    setIsDispatching(true);
    try {
      await fetch('/api/mqtt/federated/command', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ command: resetType }),
      });
      setLastActionFeedback(`Dispatched ${resetType} to edge clients`);
      appendLog('COMMAND', `Dispatched ${resetType} to federated/commands`);
      await fetchBackendStatus();
    } catch {
      setLastActionFeedback(`Dispatched ${resetType} [Offline Demo Mode]`);
      appendLog('COMMAND', `Dispatched ${resetType} (Local UI State)`);
    } finally {
      setIsDispatching(false);
    }
  };

  // Simulate a FedAvg client weight update / aggregation round step
  const handleSimulateRoundStep = () => {
    setFederatedStatus((prev) => {
      const nextReceived = prev.receivedUpdates + 1;
      const nextPending = prev.pendingUpdates + 1;
      const completedRoundNow = nextPending >= prev.expectedClients;
      const nextCompleted = completedRoundNow
        ? prev.completedRounds + 1
        : prev.completedRounds;

      if (completedRoundNow) {
        appendLog(
          'AGGREGATION',
          `FedAvg Round #${nextCompleted} completed (${prev.expectedClients}/${prev.expectedClients} clients aggregated, 250B broadcast)`
        );
      } else {
        appendLog(
          'TELEMETRY',
          `Received quantized local weight update (${nextPending}/${prev.expectedClients} pending in round)`
        );
      }

      return {
        ...prev,
        receivedUpdates: nextReceived,
        completedRounds: nextCompleted,
        pendingUpdates: completedRoundNow ? 0 : nextPending,
        globalWeightCount: MLP_TOTAL_WEIGHTS,
        lastAggregationAt: completedRoundNow
          ? new Date().toISOString()
          : prev.lastAggregationAt,
      };
    });
  };

  const handleSimulatePose = (clientId: string, pose: PoseName) => {
    ingestTelemetry({
      clientId,
      pose,
      timestamp: Date.now(),
    });
    appendLog('TELEMETRY', `${clientId} pose → ${pose}`);
  };

  const handleAssignClientRole = (role: ClientRole, clientId: string) => {
    setClientRoleMapping((prev) => ({
      ...prev,
      [role]: clientId,
    }));
  };

  // Compute the 3-Model Evaluation Summaries
  const clientALocalEval = useMemo(() => {
    const matrix =
      evaluationMode === 'REFERENCE'
        ? REFERENCE_CLIENT_A_MATRIX
        : liveClientAMatrix;
    return computeModelEvaluation(
      'CLIENT_A_LOCAL',
      'Client A Local Model (Isolated)',
      'Trained only on Client A local data',
      'Screen Up, Screen Down, Portrait Up (50/pose)',
      matrix
    );
  }, [evaluationMode, liveClientAMatrix]);

  const clientBLocalEval = useMemo(() => {
    const matrix =
      evaluationMode === 'REFERENCE'
        ? REFERENCE_CLIENT_B_MATRIX
        : liveClientBMatrix;
    return computeModelEvaluation(
      'CLIENT_B_LOCAL',
      'Client B Local Model (Isolated)',
      'Trained only on Client B local data',
      'Portrait Down, Landscape Left, Landscape Right (50/pose)',
      matrix
    );
  }, [evaluationMode, liveClientBMatrix]);

  const global8BitEval = useMemo(() => {
    const matrix =
      evaluationMode === 'REFERENCE'
        ? REFERENCE_GLOBAL_8BIT_MATRIX
        : liveGlobal8BitMatrix;
    return computeModelEvaluation(
      'FEDERATED_GLOBAL_8BIT',
      'Federated Global Model (8-Bit Quantized FedAvg)',
      'Collaboratively trained across Client A + Client B',
      'All 6 Poses via Symmetric 8-Bit FedAvg (250B/update)',
      matrix
    );
  }, [evaluationMode, liveGlobal8BitMatrix]);

  const global32BitEval = useMemo(() => {
    return computeModelEvaluation(
      'FEDERATED_GLOBAL_32BIT',
      'Federated Global Model (32-Bit Float Baseline)',
      'Uncompressed FedAvg baseline',
      'All 6 Poses via 32-Bit Float FedAvg (984B/update)',
      REFERENCE_GLOBAL_32BIT_MATRIX
    );
  }, []);

  const handleRecordEvaluationSample = (
    targetModel: 'CLIENT_A_LOCAL' | 'CLIENT_B_LOCAL' | 'FEDERATED_GLOBAL_8BIT',
    truePoseIndex: number,
    predictedPoseIndex: number
  ) => {
    const updater = (prev: ConfusionMatrix6x6) => {
      const next = cloneConfusionMatrix(prev);
      next[truePoseIndex][predictedPoseIndex] =
        (next[truePoseIndex][predictedPoseIndex] || 0) + 1;
      return next;
    };

    if (targetModel === 'CLIENT_A_LOCAL') setLiveClientAMatrix(updater);
    else if (targetModel === 'CLIENT_B_LOCAL') setLiveClientBMatrix(updater);
    else setLiveGlobal8BitMatrix(updater);

    appendLog(
      'SYSTEM',
      `Logged eval sample for ${targetModel}: True=${POSE_NAMES[truePoseIndex]}, Pred=${POSE_NAMES[predictedPoseIndex]}`
    );
  };

  const handleResetLiveEvaluation = () => {
    setLiveClientAMatrix(cloneConfusionMatrix(REFERENCE_CLIENT_A_MATRIX));
    setLiveClientBMatrix(cloneConfusionMatrix(REFERENCE_CLIENT_B_MATRIX));
    setLiveGlobal8BitMatrix(cloneConfusionMatrix(REFERENCE_GLOBAL_8BIT_MATRIX));
    appendLog('SYSTEM', 'Reset live evaluation matrices to reference baseline');
  };

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 font-sans">
      {/* Top Sticky Header */}
      <header className="bg-slate-900 text-white border-b border-slate-800 sticky top-0 z-30 shadow-md">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-mono uppercase tracking-widest px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-400/30">
                CSC 8223 • IoT Course Project
              </span>
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-400/30">
                Non-IID Federated Learning + 8-Bit Quantization
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight mt-1">
              Federated Learning Smartphone Pose Dashboard
            </h1>
            <p className="text-xs sm:text-sm text-slate-400">
              Collaborative 6-pose spatial classification across non-IID Android edge
              clients via Spring Boot &amp; Mosquitto MQTT
            </p>
          </div>

          {/* Connection & System Status Pills */}
          <div className="flex flex-wrap items-center gap-2.5">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs">
              <span
                className={`w-2.5 h-2.5 rounded-full ${
                  wsConnected ? 'bg-emerald-400 animate-pulse' : 'bg-rose-500'
                }`}
              />
              <span className="font-medium text-slate-200">
                {wsConnected ? 'WebSocket Live (/ws/telemetry)' : 'WS Disconnected'}
              </span>
            </div>

            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs">
              <span
                className={`w-2.5 h-2.5 rounded-full ${
                  backendReachable ? 'bg-emerald-400' : 'bg-amber-400'
                }`}
              />
              <span className="font-medium text-slate-200">
                {backendReachable
                  ? `FedAvg Rounds: ${federatedStatus.completedRounds}`
                  : 'Spring Boot API: Standby'}
              </span>
            </div>
          </div>
        </div>

        {/* Navigation Sub-Bar */}
        <div className="bg-slate-950/60 border-t border-slate-800">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center gap-1 overflow-x-auto py-2">
            {(
              [
                { id: 'ALL', label: 'All Sections (Full Demo View)' },
                { id: 'LIVE_DEMO', label: '1. Live Pose & MQTT Controls' },
                { id: 'EVALUATION', label: '2. Three-Model Comparison (6×6 Matrices)' },
                { id: 'QUANTIZATION', label: '3. 8-Bit Quantization Contribution' },
                { id: 'ARCHITECTURE', label: '4. Non-IID Spec & Architecture' },
              ] as { id: DashboardTab; label: string }[]
            ).map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-colors cursor-pointer ${
                  activeTab === tab.id
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </header>

      {/* Main Content Container */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-8">
        {/* Core Research Question Banner */}
        <div className="bg-gradient-to-r from-indigo-900 via-slate-900 to-teal-900 text-white rounded-xl p-4 sm:p-5 shadow-sm flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div className="space-y-1">
            <div className="text-[11px] font-mono uppercase tracking-wider text-indigo-300 font-semibold">
              Core Project Question
            </div>
            <p className="text-sm sm:text-base font-bold">
              &ldquo;Can smart devices with disjoint non-IID local sensor distributions
              collaboratively learn a global model that outperforms any isolated local
              model?&rdquo;
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3 shrink-0 text-xs font-mono">
            <div className="px-3 py-1.5 rounded-lg bg-white/10 border border-white/15">
              Client A Local:{' '}
              <strong className="text-rose-300">
                {clientALocalEval.overallAccuracy.toFixed(1)}%
              </strong>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-white/10 border border-white/15">
              Client B Local:{' '}
              <strong className="text-rose-300">
                {clientBLocalEval.overallAccuracy.toFixed(1)}%
              </strong>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-emerald-500/20 border border-emerald-400/40">
              Federated Global:{' '}
              <strong className="text-emerald-300">
                {global8BitEval.overallAccuracy.toFixed(1)}%
              </strong>
            </div>
          </div>
        </div>

        {(activeTab === 'ALL' || activeTab === 'LIVE_DEMO') && (
          <>
            <PhonePoseVisualizer
              telemetry={telemetry}
              telemetryHistory={telemetryHistory}
              packetCounts={packetCounts}
              clientRoleMapping={clientRoleMapping}
              onAssignClientRole={handleAssignClientRole}
              activeModel={activeModel}
              simulationEnabled={simulationEnabled}
              onToggleSimulation={() => setSimulationEnabled((v) => !v)}
              onSimulatePose={handleSimulatePose}
            />

            <OrchestrationControlPanel
              federatedStatus={federatedStatus}
              latestMqttMessage={latestMqttMessage}
              activeModel={activeModel}
              selectedPipeline={selectedPipeline}
              isDispatching={isDispatching}
              lastActionFeedback={lastActionFeedback}
              activityLog={activityLog}
              onStartTraining={handleStartTraining}
              onToggleModel={handleToggleModel}
              onRepublishGlobal={handleRepublishGlobal}
              onRepublishAll={handleRepublishAll}
              onSendResetCommand={handleSendResetCommand}
              onRefreshStatus={fetchBackendStatus}
              onSimulateRoundStep={handleSimulateRoundStep}
            />
          </>
        )}

        {(activeTab === 'ALL' || activeTab === 'EVALUATION') && (
          <ModelComparisonSection
            clientALocalEval={clientALocalEval}
            clientBLocalEval={clientBLocalEval}
            global8BitEval={global8BitEval}
            global32BitEval={global32BitEval}
            evaluationMode={evaluationMode}
            onSwitchEvaluationMode={setEvaluationMode}
            onRecordEvaluationSample={handleRecordEvaluationSample}
            onResetLiveEvaluation={handleResetLiveEvaluation}
            telemetry={telemetry}
            clientRoleMapping={clientRoleMapping}
          />
        )}

        {(activeTab === 'ALL' || activeTab === 'QUANTIZATION') && (
          <QuantizationContributionSection
            federatedStatus={federatedStatus}
            global8BitAccuracy={global8BitEval.overallAccuracy}
            global32BitAccuracy={global32BitEval.overallAccuracy}
            global8BitMacroF1={global8BitEval.macroF1}
            global32BitMacroF1={global32BitEval.macroF1}
          />
        )}

        {(activeTab === 'ALL' || activeTab === 'ARCHITECTURE') && (
          <SystemArchitecturePipeline />
        )}
      </main>
    </div>
  );
}

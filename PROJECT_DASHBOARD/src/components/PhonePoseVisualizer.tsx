/**
 * ============================================================================
 * FILE README: src/components/PhonePoseVisualizer.tsx
 * ============================================================================
 * Purpose:
 *   Real-time spatial orientation visualizer and non-IID distribution tracker
 *   for the two Android IoT edge clients (Client A and Client B).
 *
 * Key Responsibilities:
 *   1. Renders interactive 3D-styled phone chassis graphics (`PhoneSpatialGraphic`)
 *      that tilt/rotate according to the live predicted pose (`Screen Up`,
 *      `Screen Down`, `Portrait Up`, `Portrait Down`, `Landscape Left`,
 *      `Landscape Right`) and display the dominant gravity axis hint.
 *   2. Dynamically tracks and visualizes 6-Pose Domain Coverage for Client A
 *      and Client B, resetting coverage badges to `0 train` when `Reset Global`
 *      or `Reset All Edge` is triggered from the dashboard and restoring them
 *      when training updates or model syncs occur.
 *   3. Supports dynamic device ID binding (`EdgeClient_<id>`) and an optional
 *      Demo Telemetry Simulator for testing pose transitions without hardware.
 * ============================================================================
 */
import {
  POSE_DEFINITIONS,
  POSE_NAMES,
  type ActiveModelType,
  type ClientRole,
  type PoseDefinition,
  type PoseName,
  type TelemetryMessage,
} from '../types/federated';

interface PhonePoseVisualizerProps {
  telemetry: Record<string, TelemetryMessage>;
  telemetryHistory: Record<string, { pose: string; timestamp: number }[]>;
  packetCounts: Record<string, number>;
  clientRoleMapping: Record<ClientRole, string>;
  onAssignClientRole: (role: ClientRole, clientId: string) => void;
  activeModel: ActiveModelType;
  localModelsTrained: boolean;
  globalModelTrained: boolean;
  lastResetMode: 'NONE' | 'RESET_GLOBAL' | 'RESET_ALL';
  simulationEnabled: boolean;
  onToggleSimulation: () => void;
  onSimulatePose: (clientId: string, pose: PoseName) => void;
}

function getPoseDefinition(poseName: string): PoseDefinition | undefined {
  return POSE_DEFINITIONS.find(
    (p) => p.name.toLowerCase() === poseName.trim().toLowerCase()
  );
}

function PhoneSpatialGraphic({ poseName }: { poseName: string }) {
  const poseDef = getPoseDefinition(poseName);
  const isScreenDown = poseDef?.name === 'Screen Down';
  const isScreenUp = poseDef?.name === 'Screen Up';
  const isLandscapeLeft = poseDef?.name === 'Landscape Left';
  const isLandscapeRight = poseDef?.name === 'Landscape Right';
  const isPortraitDown = poseDef?.name === 'Portrait Down';
  const isPortraitUp = poseDef?.name === 'Portrait Up';

  // Visual orientation transform
  let containerTransform = 'rotate(0deg)';
  if (isPortraitDown) containerTransform = 'rotate(180deg)';
  else if (isLandscapeLeft) containerTransform = 'rotate(-90deg)';
  else if (isLandscapeRight) containerTransform = 'rotate(90deg)';
  else if (isScreenUp) containerTransform = 'perspective(500px) rotateX(48deg)';
  else if (isScreenDown) containerTransform = 'perspective(500px) rotateX(48deg)';

  return (
    <div className="relative flex flex-col items-center justify-center h-44 w-full select-none">
      {/* Upward reference arrow */}
      <div className="absolute top-1 left-3 flex items-center gap-1 text-[11px] font-mono text-slate-400">
        <span>↑ UP (+Y / +Z)</span>
      </div>

      {poseDef && (
        <div className="absolute top-1 right-3 text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-cyan-300 border border-slate-700">
          {poseDef.gravityHint}
        </div>
      )}

      {/* Phone chassis */}
      <div
        style={{ transform: containerTransform }}
        className={`transition-transform duration-500 ease-out w-20 h-36 rounded-2xl border-2 flex flex-col items-center justify-between p-2 shadow-lg ${
          isScreenDown
            ? 'bg-slate-800 border-amber-400/80 text-amber-200'
            : isScreenUp
            ? 'bg-gradient-to-b from-sky-900 to-indigo-950 border-sky-400 text-sky-100'
            : isPortraitUp || isPortraitDown || isLandscapeLeft || isLandscapeRight
            ? 'bg-gradient-to-b from-indigo-900 to-slate-900 border-indigo-400 text-white'
            : 'bg-slate-800/70 border-slate-600 text-slate-300'
        }`}
      >
        {/* Top edge speaker / camera */}
        <div className="w-full flex flex-col items-center gap-0.5">
          <div className="w-7 h-1 rounded-full bg-current opacity-60" />
          <span className="text-[8px] font-mono uppercase tracking-tighter opacity-75">
            TOP EDGE
          </span>
        </div>

        {/* Center face indicator */}
        <div className="flex flex-col items-center text-center px-1">
          {isScreenDown ? (
            <>
              <span className="text-[10px] font-bold uppercase tracking-wider text-amber-300">
                BACK SIDE
              </span>
              <span className="text-[9px] opacity-80">Screen Down</span>
            </>
          ) : isScreenUp ? (
            <>
              <span className="text-[10px] font-bold uppercase tracking-wider text-sky-300">
                FLAT UP
              </span>
              <span className="text-[9px] opacity-80">Screen Up</span>
            </>
          ) : poseDef ? (
            <>
              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-200">
                {isLandscapeLeft || isLandscapeRight ? 'HORIZONTAL' : 'VERTICAL'}
              </span>
              <span className="text-[9px] opacity-80">{poseDef.shortName}</span>
            </>
          ) : (
            <span className="text-[10px] font-mono opacity-75">{poseName}</span>
          )}
        </div>

        {/* Home bar at bottom edge */}
        <div className="w-8 h-1 rounded-full bg-current opacity-50" />
      </div>
    </div>
  );
}

export default function PhonePoseVisualizer({
  telemetry,
  telemetryHistory,
  packetCounts,
  clientRoleMapping,
  onAssignClientRole,
  activeModel,
  localModelsTrained,
  globalModelTrained,
  lastResetMode,
  simulationEnabled,
  onToggleSimulation,
  onSimulatePose,
}: PhonePoseVisualizerProps) {
  const discoveredClientIds = Object.keys(telemetry);

  const clientCards: {
    role: ClientRole;
    title: string;
    subtitle: string;
    primaryPoses: PoseName[];
    assignedClientId: string;
  }[] = [
    {
      role: 'CLIENT_A',
      title: 'Client A (Edge Device 1)',
      subtitle: 'Primary Non-IID Training Poses: Screen Up, Screen Down, Portrait Up',
      primaryPoses: ['Screen Up', 'Screen Down', 'Portrait Up'],
      assignedClientId: clientRoleMapping.CLIENT_A,
    },
    {
      role: 'CLIENT_B',
      title: 'Client B (Edge Device 2)',
      subtitle: 'Primary Non-IID Training Poses: Portrait Down, Landscape Left, Landscape Right',
      primaryPoses: ['Portrait Down', 'Landscape Left', 'Landscape Right'],
      assignedClientId: clientRoleMapping.CLIENT_B,
    },
  ];

  // Any extra connected clients beyond the two mapped ones
  const mappedIds = new Set([clientRoleMapping.CLIENT_A, clientRoleMapping.CLIENT_B]);
  const extraClientIds = discoveredClientIds.filter((id) => !mappedIds.has(id));

  return (
    <section className="space-y-6">
      {/* Section Header + Simulation Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex flex-wrap items-center gap-2">
            <span>Live Edge Device Spatial Pose Telemetry</span>
            <span className="text-xs font-mono px-2.5 py-0.5 rounded-full bg-indigo-100 text-indigo-800 border border-indigo-200">
              Active Inference: {activeModel} MODEL
            </span>
            {lastResetMode === 'RESET_ALL' && (
              <span className="text-xs font-mono px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-800 border border-rose-300">
                All Edge Models Reset (0/6 Poses Trained)
              </span>
            )}
            {lastResetMode === 'RESET_GLOBAL' && (
              <span className="text-xs font-mono px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                Global Model Reset (0/6 Global Poses)
              </span>
            )}
          </h2>
          <p className="text-sm text-slate-600">
            Real-time predictions streamed from Android edge clients via MQTT{' '}
            <code className="text-xs bg-slate-200 px-1.5 py-0.5 rounded">
              federated/telemetry/#
            </code>{' '}
            → Spring Boot WebSocket{' '}
            <code className="text-xs bg-slate-200 px-1.5 py-0.5 rounded">
              /ws/telemetry
            </code>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 self-start sm:self-auto">
          <button
            type="button"
            onClick={() =>
              onAssignClientRole('CLIENT_A', clientRoleMapping.CLIENT_B)
            }
            className="px-3.5 py-2 rounded-lg text-xs font-semibold border bg-white text-slate-700 border-slate-300 hover:bg-slate-50 transition-colors cursor-pointer"
            title="Swap which EdgeClient ID is assigned to Client A vs Client B"
          >
            ⇄ Swap Client A &amp; B IDs
          </button>

          <button
            type="button"
            onClick={onToggleSimulation}
            className={`px-3.5 py-2 rounded-lg text-xs font-semibold border transition-colors cursor-pointer ${
              simulationEnabled
                ? 'bg-amber-500 text-white border-amber-600 hover:bg-amber-600'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}
          >
            {simulationEnabled
              ? '● Demo Telemetry Simulator: ON'
              : '○ Enable Demo Pose Simulator'}
          </button>
        </div>
      </div>

      {/* Two Primary Non-IID Client Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {clientCards.map((card) => {
          const data = telemetry[card.assignedClientId];
          const currentPose = data?.pose || 'Waiting for telemetry...';
          const poseDef = getPoseDefinition(currentPose);
          const isPrimaryPose = poseDef
            ? card.primaryPoses.includes(poseDef.name)
            : null;
          const history = telemetryHistory[card.assignedClientId] || [];
          const count = packetCounts[card.assignedClientId] || 0;

          // Determine if this client's primary poses are currently trained
          // If RESET_ALL was triggered (not localModelsTrained) or if RESET_GLOBAL was triggered while viewing GLOBAL model, reflect reset
          const showResetAll = !localModelsTrained;
          const showResetGlobal = !globalModelTrained && lastResetMode === 'RESET_GLOBAL';

          return (
            <div
              key={card.role}
              className={`bg-white rounded-xl shadow-sm border-2 transition-all overflow-hidden flex flex-col justify-between ${
                card.role === 'CLIENT_A'
                  ? 'border-blue-500/80'
                  : 'border-emerald-500/80'
              }`}
            >
              {/* Card Top Bar */}
              <div
                className={`px-5 py-3.5 border-b flex flex-wrap items-center justify-between gap-2 ${
                  card.role === 'CLIENT_A'
                    ? 'bg-blue-50/70 border-blue-100'
                    : 'bg-emerald-50/70 border-emerald-100'
                }`}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-block w-2.5 h-2.5 rounded-full ${
                        data ? 'bg-green-500 animate-pulse' : 'bg-slate-400'
                      }`}
                    />
                    <h3 className="font-bold text-slate-900 text-base">
                      {card.title}
                    </h3>
                    <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-900 text-white">
                      {card.assignedClientId}
                    </span>
                  </div>
                  <p className="text-xs text-slate-600 mt-0.5">{card.subtitle}</p>
                </div>

                {/* Device ID selector if multiple MQTT clientIds appear */}
                {discoveredClientIds.length > 0 && (
                  <div className="flex items-center gap-1.5 text-xs">
                    <label
                      htmlFor={`bind-${card.role}`}
                      className="text-slate-500 font-medium"
                    >
                      Bind ID:
                    </label>
                    <select
                      id={`bind-${card.role}`}
                      value={card.assignedClientId}
                      onChange={(e) =>
                        onAssignClientRole(card.role, e.target.value)
                      }
                      className="bg-white border border-slate-300 rounded px-2 py-1 font-mono text-xs text-slate-800"
                    >
                      {!discoveredClientIds.includes(card.assignedClientId) && (
                        <option value={card.assignedClientId}>
                          {card.assignedClientId} (default)
                        </option>
                      )}
                      {discoveredClientIds.map((id) => (
                        <option key={id} value={id}>
                          {id}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Main Pose Display + 3D Spatial Graphic */}
              <div className="p-5 space-y-4 flex-1 flex flex-col justify-between">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-center bg-slate-900 text-white rounded-xl p-4">
                  <PhoneSpatialGraphic poseName={currentPose} />

                  <div className="space-y-2.5 text-center sm:text-left">
                    <div className="text-xs uppercase tracking-wider text-slate-400 font-semibold">
                      Detected Spatial Pose
                    </div>
                    <div className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">
                      {currentPose}
                    </div>
                    {poseDef ? (
                      <p className="text-xs text-slate-300 leading-relaxed">
                        {poseDef.definition}
                      </p>
                    ) : (
                      <p className="text-xs text-slate-400">
                        Connect Android app to broker or click a pose below to test.
                      </p>
                    )}

                    {/* Non-IID Distribution & Reset Context Badge */}
                    <div className="pt-1">
                      {showResetAll ? (
                        <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-rose-500/20 text-rose-300 border border-rose-500/40">
                          ✕ Local &amp; Global Weights Reset (0/50 trained across all 6
                          poses)
                        </span>
                      ) : showResetGlobal ? (
                        <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-amber-500/20 text-amber-300 border border-amber-500/40">
                          ⟲ Global Model Reset (0/6 global coverage — awaiting FedAvg or
                          republish)
                        </span>
                      ) : isPrimaryPose !== null ? (
                        isPrimaryPose ? (
                          <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                            ✓ In {card.role === 'CLIENT_A' ? 'Client A' : 'Client B'}{' '}
                            Local Training Distribution (50 samples)
                          </span>
                        ) : globalModelTrained && activeModel === 'GLOBAL' ? (
                          <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-teal-500/20 text-teal-300 border border-teal-500/40">
                            ⇄ Covered by Federated Global Model (0 local / 50 peer
                            samples)
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-amber-500/20 text-amber-300 border border-amber-500/40">
                            ⚠ Out-of-Distribution Locally (0 samples on{' '}
                            {card.role === 'CLIENT_A' ? 'Client A' : 'Client B'} — Needs
                            GLOBAL Model)
                          </span>
                        )
                      ) : null}
                    </div>
                  </div>
                </div>

                {/* Non-IID 6-Pose Domain Coverage Chips */}
                <div>
                  <div className="flex flex-wrap items-center justify-between gap-1 text-xs text-slate-500 mb-1.5">
                    <span className="font-semibold uppercase tracking-wider">
                      6-Pose Domain Coverage (
                      {card.role === 'CLIENT_A' ? 'Client A' : 'Client B'})
                    </span>
                    <div className="flex flex-wrap items-center gap-2">
                      {showResetAll ? (
                        <span className="text-rose-600 font-bold font-mono">
                          ✕ Reset All Edge: Training 0/50
                        </span>
                      ) : showResetGlobal ? (
                        <span className="text-amber-700 font-bold font-mono">
                          ⟲ Reset Global: Global 0/6 • Local Train 50/50
                        </span>
                      ) : (
                        <span className="font-mono text-[11px]">
                          Primary Train: <strong>50/50</strong> • Unseen Train:{' '}
                          <strong>0/50</strong>
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-1.5">
                    {POSE_NAMES.map((pose) => {
                      const isPrimary = card.primaryPoses.includes(pose);
                      const isCurrent =
                        currentPose.toLowerCase() === pose.toLowerCase();

                      let chipStyle =
                        'bg-slate-100 text-slate-600 border-slate-200 hover:bg-slate-200';
                      let trainText = isPrimary ? 'Train: 50/50' : 'Train: 0/50';

                      if (showResetAll) {
                        chipStyle =
                          'bg-rose-50/70 text-rose-900 border-dashed border-rose-300 hover:bg-rose-100/70';
                        trainText = 'Train: 0/50';
                      } else if (showResetGlobal) {
                        chipStyle = isPrimary
                          ? 'bg-amber-50 text-amber-900 border-dashed border-amber-300 hover:bg-amber-100'
                          : 'bg-slate-100 text-slate-400 border-dashed border-slate-300 hover:bg-slate-200';
                        trainText = isPrimary ? 'Train: 50/50' : 'Train: 0/50';
                      } else if (isPrimary && localModelsTrained) {
                        chipStyle =
                          'bg-emerald-50 text-emerald-900 border-emerald-300 hover:bg-emerald-100';
                        trainText = '✓ Train: 50/50';
                      } else if (
                        !isPrimary &&
                        globalModelTrained &&
                        activeModel === 'GLOBAL'
                      ) {
                        chipStyle =
                          'bg-teal-50 text-teal-900 border-teal-300 hover:bg-teal-100';
                        trainText = 'Train: 0/50 (FL)';
                      }

                      return (
                        <button
                          key={pose}
                          type="button"
                          onClick={() =>
                            onSimulatePose(card.assignedClientId, pose)
                          }
                          title={`${pose} — click to simulate pose on ${card.assignedClientId}`}
                          className={`px-2 py-1.5 rounded-lg text-[11px] font-medium border text-center transition-all cursor-pointer ${
                            isCurrent
                              ? 'ring-2 ring-offset-1 ring-indigo-600 font-bold '
                              : ''
                          }${chipStyle}`}
                        >
                          <div className="truncate font-semibold">{pose}</div>
                          <div className="text-[10px] font-mono mt-0.5">
                            {trainText}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Recent Telemetry Stream & Timestamp Footer */}
                <div className="pt-2 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500 font-mono">
                  <div className="flex items-center gap-1.5 overflow-x-auto">
                    <span className="text-slate-400">Recent:</span>
                    {history.length === 0 ? (
                      <span className="text-slate-400 italic">No packets yet</span>
                    ) : (
                      history.slice(0, 4).map((h, i) => (
                        <span
                          key={`${h.timestamp}-${i}`}
                          className="px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 text-[11px]"
                        >
                          {h.pose}
                        </span>
                      ))
                    )}
                  </div>
                  <div>
                    Packets: <strong className="text-slate-700">{count}</strong> •{' '}
                    {data
                      ? `Updated ${new Date(data.timestamp).toLocaleTimeString()}`
                      : 'Awaiting MQTT'}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Extra Connected Clients (if additional phones/devices connect) */}
      {extraClientIds.length > 0 && (
        <div className="bg-white rounded-xl p-4 shadow-sm border border-slate-200">
          <h3 className="text-sm font-bold text-slate-700 uppercase tracking-wider mb-3">
            Additional Connected Edge Devices ({extraClientIds.length})
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {extraClientIds.map((id) => {
              const item = telemetry[id];
              return (
                <div
                  key={id}
                  className="p-3 rounded-lg border border-slate-200 bg-slate-50 flex items-center justify-between"
                >
                  <div>
                    <div className="font-mono text-xs font-bold text-slate-800">
                      {id}
                    </div>
                    <div className="text-base font-extrabold text-indigo-600">
                      {item?.pose}
                    </div>
                    <div className="text-[11px] text-slate-400 font-mono">
                      {item ? new Date(item.timestamp).toLocaleTimeString() : ''}
                    </div>
                  </div>
                  <div className="flex flex-col gap-1">
                    <button
                      type="button"
                      onClick={() => onAssignClientRole('CLIENT_A', id)}
                      className="text-[11px] px-2 py-1 rounded bg-blue-100 text-blue-800 hover:bg-blue-200 font-medium cursor-pointer"
                    >
                      Set as Client A
                    </button>
                    <button
                      type="button"
                      onClick={() => onAssignClientRole('CLIENT_B', id)}
                      className="text-[11px] px-2 py-1 rounded bg-emerald-100 text-emerald-800 hover:bg-emerald-200 font-medium cursor-pointer"
                    >
                      Set as Client B
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}

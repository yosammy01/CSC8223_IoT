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
 *   2. Displays whether the currently detected pose belongs to the device's
 *      primary local training distribution (50 samples — Seen Locally) or is
 *      out-of-distribution (0 samples — Unseen Locally, requiring the
 *      Federated Global Model).
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
                VERTICAL
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
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <span>Live Edge Device Spatial Pose Telemetry</span>
            <span className="text-xs font-mono px-2.5 py-0.5 rounded-full bg-indigo-100 text-indigo-800 border border-indigo-200">
              Active Inference: {activeModel} MODEL
            </span>
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

        <button
          type="button"
          onClick={onToggleSimulation}
          className={`px-3.5 py-2 rounded-lg text-xs font-semibold border transition-colors cursor-pointer self-start sm:self-auto ${
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

      {/* Two Primary Non-IID Client Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {clientCards.map((card) => {
          const data = telemetry[card.assignedClientId];
          const currentPose = data?.pose || 'Waiting for telemetry...';
          const poseDef = getPoseDefinition(currentPose);
          const isSeenLocally = poseDef
            ? card.primaryPoses.includes(poseDef.name)
            : null;
          const history = telemetryHistory[card.assignedClientId] || [];
          const count = packetCounts[card.assignedClientId] || 0;

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

                    {/* Non-IID Distribution Context Badge */}
                    {isSeenLocally !== null && (
                      <div className="pt-1">
                        {isSeenLocally ? (
                          <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                            ✓ In {card.role === 'CLIENT_A' ? 'Client A' : 'Client B'}{' '}
                            Local Training Distribution (50 samples)
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-md bg-amber-500/20 text-amber-300 border border-amber-500/40">
                            ⚠ Out-of-Distribution Locally (0 samples on{' '}
                            {card.role === 'CLIENT_A' ? 'Client A' : 'Client B'} — Needs
                            GLOBAL Model)
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                {/* Non-IID Local Training Poses Chips */}
                <div>
                  <div className="flex items-center justify-between text-xs text-slate-500 mb-1.5">
                    <span className="font-semibold uppercase tracking-wider">
                      6-Pose Domain Coverage ({card.role === 'CLIENT_A' ? 'Client A' : 'Client B'})
                    </span>
                    <span>
                      Green = Primary Local Training (50/pose) • Gray = Unseen Locally (0/pose)
                    </span>
                  </div>
                  <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5">
                    {POSE_NAMES.map((pose) => {
                      const isPrimary = card.primaryPoses.includes(pose);
                      const isCurrent =
                        currentPose.toLowerCase() === pose.toLowerCase();
                      return (
                        <button
                          key={pose}
                          type="button"
                          onClick={() =>
                            onSimulatePose(card.assignedClientId, pose)
                          }
                          title={
                            simulationEnabled
                              ? `Simulate ${pose} on ${card.assignedClientId}`
                              : `${pose} (${
                                  isPrimary
                                    ? 'Primary local training pose'
                                    : 'Unseen in local training'
                                }) — click to simulate`
                          }
                          className={`px-2 py-1.5 rounded-lg text-[11px] font-medium border text-center transition-all cursor-pointer ${
                            isCurrent
                              ? 'ring-2 ring-offset-1 ring-indigo-600 font-bold '
                              : ''
                          }${
                            isPrimary
                              ? 'bg-emerald-50 text-emerald-900 border-emerald-300 hover:bg-emerald-100'
                              : 'bg-slate-100 text-slate-500 border-slate-200 hover:bg-slate-200'
                          }`}
                        >
                          <div className="truncate">{pose}</div>
                          <div className="text-[9px] font-mono opacity-75">
                            {isPrimary ? '50 train' : '0 train'}
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


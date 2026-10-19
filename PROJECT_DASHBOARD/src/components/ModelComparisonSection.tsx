/**
 * ============================================================================
 * FILE README: src/components/ModelComparisonSection.tsx
 * ============================================================================
 * Purpose:
 *   Evaluates and compares the three required models specified in Section 6 & 7
 *   of the CSC 8223 Course Project specification:
 *     1. Client A Local Model (trained only on Screen Up, Screen Down, Portrait Up)
 *     2. Client B Local Model (trained only on Portrait Down, Landscape Left, Landscape Right)
 *     3. Federated Global Model (trained collaboratively via FedAvg across both clients)
 *
 * Key Features:
 *   - Renders side-by-side 6x6 Confusion Matrices (`ConfusionMatrixCard`),
 *     Overall Accuracy, Macro-F1 Score, Seen vs. Unseen Pose Accuracy, and
 *     Per-Pose Accuracy bar charts.
 *   - Supports toggling between the Reference Benchmark (60 test samples) and
 *     Interactive/Live Capture Mode to log live WebSocket predictions from
 *     Client A and Client B during the in-class demonstration.
 *   - Includes inputs to record on-device calibration accuracies from the
 *     Android `Eval / Calib` screen (`MainActivity.kt`).
 * ============================================================================
 */
import { useState } from 'react';
import {
  POSE_DEFINITIONS,
  POSE_NAMES,
  type ClientRole,
  type ModelEvaluationSummary,
  type TelemetryMessage,
} from '../types/federated';

interface ModelComparisonSectionProps {
  clientALocalEval: ModelEvaluationSummary;
  clientBLocalEval: ModelEvaluationSummary;
  global8BitEval: ModelEvaluationSummary;
  global32BitEval: ModelEvaluationSummary;
  evaluationMode: 'REFERENCE' | 'LIVE';
  onSwitchEvaluationMode: (mode: 'REFERENCE' | 'LIVE') => void;
  onRecordEvaluationSample: (
    targetModel:
      | 'CLIENT_A_LOCAL'
      | 'CLIENT_B_LOCAL'
      | 'FEDERATED_GLOBAL_8BIT'
      | 'FEDERATED_GLOBAL_32BIT',
    truePoseIndex: number,
    predictedPoseIndex: number
  ) => void;
  onResetLiveEvaluation: () => void;
  telemetry: Record<string, TelemetryMessage>;
  clientRoleMapping: Record<ClientRole, string>;
}

const SHORT_POSE_LABELS = ['SU', 'SD', 'PU', 'PD', 'LL', 'LR'];

function ConfusionMatrixCard({
  summary,
  accentColor,
}: {
  summary: ModelEvaluationSummary;
  accentColor: 'blue' | 'emerald' | 'teal';
}) {
  const headerStyles = {
    blue: 'bg-blue-50 border-blue-200 text-blue-950',
    emerald: 'bg-emerald-50 border-emerald-200 text-emerald-950',
    teal: 'bg-teal-50 border-teal-200 text-teal-950',
  }[accentColor];

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden flex flex-col justify-between">
      <div className={`px-4 py-3 border-b ${headerStyles}`}>
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-bold text-sm">{summary.title}</h3>
          <span className="text-xs font-mono font-bold px-2 py-0.5 rounded bg-white/90 border border-slate-200">
            Acc: {summary.overallAccuracy.toFixed(1)}%
          </span>
        </div>
        <p className="text-[11px] opacity-80 mt-0.5">{summary.trainedOnDescription}</p>
      </div>

      {/* Key Metrics Strip */}
      <div className="grid grid-cols-3 border-b border-slate-100 bg-slate-50/60 text-center py-2 px-2 text-xs">
        <div>
          <div className="text-[10px] uppercase text-slate-400 font-semibold">
            Macro-F1
          </div>
          <div className="font-extrabold text-slate-800">
            {summary.macroF1.toFixed(1)}%
          </div>
        </div>
        <div className="border-x border-slate-200">
          <div className="text-[10px] uppercase text-slate-400 font-semibold">
            Client A Poses
          </div>
          <div
            className={`font-extrabold ${
              summary.clientAPosesAccuracy >= 80
                ? 'text-emerald-600'
                : 'text-rose-600'
            }`}
          >
            {summary.clientAPosesAccuracy.toFixed(1)}%
          </div>
        </div>
        <div>
          <div className="text-[10px] uppercase text-slate-400 font-semibold">
            Client B Poses
          </div>
          <div
            className={`font-extrabold ${
              summary.clientBPosesAccuracy >= 80
                ? 'text-emerald-600'
                : 'text-rose-600'
            }`}
          >
            {summary.clientBPosesAccuracy.toFixed(1)}%
          </div>
        </div>
      </div>

      {/* 6x6 Confusion Matrix Table */}
      <div className="p-3 overflow-x-auto">
        <div className="text-[10px] font-mono text-slate-400 mb-1.5 flex justify-between">
          <span>Rows: Ground Truth Pose</span>
          <span>Cols: Predicted Pose</span>
        </div>
        <table className="w-full border-collapse text-center font-mono text-xs">
          <thead>
            <tr>
              <th className="p-1 text-[10px] text-slate-400 border border-slate-200 bg-slate-50">
                T \ P
              </th>
              {SHORT_POSE_LABELS.map((lbl, idx) => (
                <th
                  key={lbl}
                  title={`Predicted: ${POSE_NAMES[idx]}`}
                  className={`p-1 text-[10px] font-bold border border-slate-200 ${
                    idx < 3
                      ? 'bg-blue-50/60 text-blue-900'
                      : 'bg-emerald-50/60 text-emerald-900'
                  }`}
                >
                  {lbl}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {summary.confusionMatrix.map((row, rIdx) => {
              const rowSum = row.reduce((a, b) => a + b, 0);
              return (
                <tr key={SHORT_POSE_LABELS[rIdx]}>
                  <th
                    title={`True Pose: ${POSE_NAMES[rIdx]}`}
                    className={`p-1 text-[10px] font-bold border border-slate-200 ${
                      rIdx < 3
                        ? 'bg-blue-50/60 text-blue-900'
                        : 'bg-emerald-50/60 text-emerald-900'
                    }`}
                  >
                    {SHORT_POSE_LABELS[rIdx]}
                  </th>
                  {row.map((val, cIdx) => {
                    const isDiag = rIdx === cIdx;
                    let cellClass = 'bg-white text-slate-300';
                    if (val > 0) {
                      if (isDiag) {
                        cellClass =
                          'bg-emerald-600 text-white font-bold';
                      } else {
                        cellClass =
                          'bg-rose-100 text-rose-800 font-semibold';
                      }
                    } else if (isDiag && rowSum > 0) {
                      cellClass = 'bg-rose-50/70 text-rose-400 font-semibold';
                    }
                    return (
                      <td
                        key={`${rIdx}-${cIdx}`}
                        title={`True: ${POSE_NAMES[rIdx]} → Predicted: ${POSE_NAMES[cIdx]} (${val})`}
                        className={`p-1.5 border border-slate-200 ${cellClass}`}
                      >
                        {val}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Per-Class Accuracy Footer */}
      <div className="px-3 pb-3 pt-1">
        <div className="text-[10px] font-semibold uppercase text-slate-400 mb-1">
          Per-Class Recall / F1 Breakdown (N={summary.totalSamples})
        </div>
        <div className="grid grid-cols-2 gap-1 text-[11px]">
          {summary.perClass.map((cls) => (
            <div
              key={cls.poseName}
              className="flex items-center justify-between bg-slate-50 px-2 py-1 rounded border border-slate-100"
            >
              <span className="truncate text-slate-700 font-medium">
                {cls.poseName}
              </span>
              <span
                className={`font-mono font-bold ${
                  cls.accuracy >= 80
                    ? 'text-emerald-700'
                    : cls.accuracy > 0
                    ? 'text-amber-700'
                    : 'text-rose-600'
                }`}
              >
                {cls.accuracy.toFixed(0)}% (F1 {cls.f1.toFixed(0)}%)
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function ModelComparisonSection({
  clientALocalEval,
  clientBLocalEval,
  global8BitEval,
  global32BitEval,
  evaluationMode,
  onSwitchEvaluationMode,
  onRecordEvaluationSample,
  onResetLiveEvaluation,
  telemetry,
  clientRoleMapping,
}: ModelComparisonSectionProps) {
  const [selectedTruePoseIdx, setSelectedTruePoseIdx] = useState<number>(0);
  const [selectedEvalTargetModel, setSelectedEvalTargetModel] = useState<
    | 'CLIENT_A_LOCAL'
    | 'CLIENT_B_LOCAL'
    | 'FEDERATED_GLOBAL_8BIT'
    | 'FEDERATED_GLOBAL_32BIT'
  >('FEDERATED_GLOBAL_8BIT');
  const [manualPredPoseIdx, setManualPredPoseIdx] = useState<number>(0);

  const resolvePoseIndex = (poseName: string | undefined): number | null => {
    if (!poseName) return null;
    const idx = POSE_NAMES.findIndex(
      (p) => p.toLowerCase() === poseName.trim().toLowerCase()
    );
    return idx >= 0 ? idx : null;
  };

  const clientAPoseName = telemetry[clientRoleMapping.CLIENT_A]?.pose;
  const clientBPoseName = telemetry[clientRoleMapping.CLIENT_B]?.pose;

  const handleCaptureFromDevice = (role: ClientRole) => {
    const clientId = clientRoleMapping[role];
    const poseStr = telemetry[clientId]?.pose;
    const predIdx = resolvePoseIndex(poseStr);
    if (predIdx !== null) {
      onRecordEvaluationSample(selectedEvalTargetModel, selectedTruePoseIdx, predIdx);
    }
  };

  return (
    <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
      {/* Header & Mode Switch */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-slate-900">
              Three-Model Non-IID Evaluation & Confusion Matrix Comparison
            </h2>
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-teal-100 text-teal-800 border border-teal-200">
              Core Research Question
            </span>
          </div>
          <p className="text-sm text-slate-600 mt-1">
            Compares isolated <strong>Client A Local Model</strong> (trained only on Screen
            Up, Screen Down, Portrait Up), isolated <strong>Client B Local Model</strong>{' '}
            (trained only on Portrait Down, Landscape Left, Landscape Right), and the{' '}
            <strong>Federated Global Model</strong> across all 6 poses.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => onSwitchEvaluationMode('LIVE')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors cursor-pointer ${
              evaluationMode === 'LIVE'
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}
          >
            Interactive / Live Capture (Saved Locally)
          </button>
          <button
            type="button"
            onClick={() => onSwitchEvaluationMode('REFERENCE')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors cursor-pointer ${
              evaluationMode === 'REFERENCE'
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}
          >
            Reference Benchmark (60 samples)
          </button>
        </div>
      </div>

      {/* Live Evaluation Capture Toolbar (shown when Interactive/Live Capture mode is active) */}
      {evaluationMode === 'LIVE' && (
        <div className="p-4 rounded-xl bg-indigo-50/80 border border-indigo-200 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 className="text-sm font-bold text-indigo-950">
                Live Demo Prediction Recorder (Starts at 0 • Auto-Saved in Browser)
              </h3>
              <p className="text-xs text-indigo-800">
                Select the physical ground-truth pose and target model, then capture live
                telemetry from Client A / Client B or log a prediction sample.
              </p>
            </div>
            <button
              type="button"
              onClick={onResetLiveEvaluation}
              className="text-xs px-2.5 py-1 rounded bg-white hover:bg-rose-50 text-rose-700 border border-rose-200 font-semibold cursor-pointer"
            >
              Clear Live Matrices (Reset to 0)
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-semibold text-indigo-900 mb-1">
                1. Model Being Evaluated
              </label>
              <select
                value={selectedEvalTargetModel}
                onChange={(e) =>
                  setSelectedEvalTargetModel(
                    e.target.value as
                      | 'CLIENT_A_LOCAL'
                      | 'CLIENT_B_LOCAL'
                      | 'FEDERATED_GLOBAL_8BIT'
                      | 'FEDERATED_GLOBAL_32BIT'
                  )
                }
                className="w-full bg-white border border-indigo-300 rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-800"
              >
                <option value="CLIENT_A_LOCAL">Client A Local Model (Isolated)</option>
                <option value="CLIENT_B_LOCAL">Client B Local Model (Isolated)</option>
                <option value="FEDERATED_GLOBAL_8BIT">
                  Federated Global Model (8-Bit Quantized)
                </option>
                <option value="FEDERATED_GLOBAL_32BIT">
                  Federated Global Model (32-Bit Baseline)
                </option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-indigo-900 mb-1">
                2. Ground-Truth Physical Pose
              </label>
              <select
                value={selectedTruePoseIdx}
                onChange={(e) => setSelectedTruePoseIdx(Number(e.target.value))}
                className="w-full bg-white border border-indigo-300 rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-800"
              >
                {POSE_NAMES.map((name, idx) => (
                  <option key={name} value={idx}>
                    {name} ({idx < 3 ? 'Client A Primary' : 'Client B Primary'})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-indigo-900 mb-1">
                3. Record Prediction into Matrix
              </label>
              <div className="flex items-center gap-1.5">
                <select
                  value={manualPredPoseIdx}
                  onChange={(e) => setManualPredPoseIdx(Number(e.target.value))}
                  className="bg-white border border-indigo-300 rounded-lg px-2 py-1.5 text-xs font-medium text-slate-800 flex-1"
                >
                  {POSE_NAMES.map((name, idx) => (
                    <option key={name} value={idx}>
                      Pred: {name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() =>
                    onRecordEvaluationSample(
                      selectedEvalTargetModel,
                      selectedTruePoseIdx,
                      manualPredPoseIdx
                    )
                  }
                  className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold cursor-pointer shrink-0"
                >
                  + Log Sample
                </button>
              </div>
            </div>
          </div>

          {/* Direct WebSocket Telemetry Capture Buttons */}
          <div className="flex flex-wrap items-center gap-2 pt-1">
            <button
              type="button"
              disabled={resolvePoseIndex(clientAPoseName) === null}
              onClick={() => handleCaptureFromDevice('CLIENT_A')}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 disabled:bg-slate-300 text-white hover:bg-blue-700 cursor-pointer"
            >
              Capture Live from Client A ({clientAPoseName || 'No Pose'})
            </button>
            <button
              type="button"
              disabled={resolvePoseIndex(clientBPoseName) === null}
              onClick={() => handleCaptureFromDevice('CLIENT_B')}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-600 disabled:bg-slate-300 text-white hover:bg-emerald-700 cursor-pointer"
            >
              Capture Live from Client B ({clientBPoseName || 'No Pose'})
            </button>
          </div>
        </div>
      )}

      {/* High-Level Comparative Summary Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse text-xs">
          <thead>
            <tr className="bg-slate-100 text-slate-700 border-y border-slate-200">
              <th className="py-2.5 px-3 font-bold">Model Variant</th>
              <th className="py-2.5 px-3 font-bold">Training Distribution</th>
              <th className="py-2.5 px-3 font-bold text-right">
                Client A Poses (SU, SD, PU)
              </th>
              <th className="py-2.5 px-3 font-bold text-right">
                Client B Poses (PD, LL, LR)
              </th>
              <th className="py-2.5 px-3 font-bold text-right">Overall Accuracy</th>
              <th className="py-2.5 px-3 font-bold text-right">Macro-F1 Score</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200">
            {[
              clientALocalEval,
              clientBLocalEval,
              global8BitEval,
              global32BitEval,
            ].map((m) => {
              const isGlobal = m.modelId.startsWith('FEDERATED_GLOBAL');
              return (
                <tr
                  key={m.modelId}
                  className={isGlobal ? 'bg-teal-50/40 font-medium' : 'bg-white'}
                >
                  <td className="py-2.5 px-3 font-bold text-slate-900">
                    {m.title}
                  </td>
                  <td className="py-2.5 px-3 text-slate-600">
                    {m.trainedOnDescription}
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono font-bold">
                    <span
                      className={
                        m.clientAPosesAccuracy >= 80
                          ? 'text-emerald-700'
                          : 'text-rose-600'
                      }
                    >
                      {m.clientAPosesAccuracy.toFixed(1)}%
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono font-bold">
                    <span
                      className={
                        m.clientBPosesAccuracy >= 80
                          ? 'text-emerald-700'
                          : 'text-rose-600'
                      }
                    >
                      {m.clientBPosesAccuracy.toFixed(1)}%
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono font-extrabold text-slate-900">
                    {m.overallAccuracy.toFixed(1)}%
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono font-extrabold text-indigo-700">
                    {m.macroF1.toFixed(1)}%
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Per-Pose Visual Accuracy Comparison Across the 3 Required Models */}
      <div className="bg-slate-50 rounded-xl p-4 border border-slate-200 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
            Per-Pose Classification Accuracy: Isolated Local Models vs. Federated Global Model
          </h3>
          <div className="flex items-center gap-4 text-xs">
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-sm bg-blue-500 inline-block" />
              Client A Local
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-sm bg-emerald-500 inline-block" />
              Client B Local
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-sm bg-teal-600 inline-block" />
              Federated Global (8-bit FedAvg)
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3">
          {POSE_DEFINITIONS.map((pose, idx) => {
            const accA = clientALocalEval.perClass[idx]?.accuracy || 0;
            const accB = clientBLocalEval.perClass[idx]?.accuracy || 0;
            const accG = global8BitEval.perClass[idx]?.accuracy || 0;

            return (
              <div
                key={pose.name}
                className="bg-white p-3 rounded-lg border border-slate-200 space-y-2"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-800 truncate">
                    {pose.name}
                  </span>
                  <span
                    className={`text-[9px] font-mono px-1.5 py-0.5 rounded ${
                      pose.primaryClient === 'CLIENT_A'
                        ? 'bg-blue-100 text-blue-800'
                        : 'bg-emerald-100 text-emerald-800'
                    }`}
                  >
                    {pose.primaryClient === 'CLIENT_A' ? 'Client A' : 'Client B'}
                  </span>
                </div>

                <div className="space-y-1.5 text-[11px] font-mono">
                  <div>
                    <div className="flex justify-between text-slate-600">
                      <span>Local A</span>
                      <span>{accA.toFixed(0)}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-blue-500"
                        style={{ width: `${accA}%` }}
                      />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-slate-600">
                      <span>Local B</span>
                      <span>{accB.toFixed(0)}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-emerald-500"
                        style={{ width: `${accB}%` }}
                      />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between font-bold text-teal-800">
                      <span>Global FL</span>
                      <span>{accG.toFixed(0)}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-teal-600"
                        style={{ width: `${accG}%` }}
                      />
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Three Side-by-Side 6x6 Confusion Matrices */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <ConfusionMatrixCard summary={clientALocalEval} accentColor="blue" />
        <ConfusionMatrixCard summary={clientBLocalEval} accentColor="emerald" />
        <ConfusionMatrixCard summary={global8BitEval} accentColor="teal" />
      </div>
    </section>
  );
}

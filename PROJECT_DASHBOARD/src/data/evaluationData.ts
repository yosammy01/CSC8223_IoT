/**
 * ============================================================================
 * FILE README: src/data/evaluationData.ts
 * ============================================================================
 * Purpose:
 *   Provides evaluation metric computations, reference Non-IID experimental
 *   confusion matrices, and the client-side symmetric 8-bit quantization
 *   simulator for PROJECT_DASHBOARD.
 *
 * Key Exports:
 *   1. `computeModelEvaluation(...)`: Computes Overall Accuracy, Per-Class
 *      Recall/Accuracy, Per-Class Precision, Per-Class F1, Macro-F1 Score,
 *      and Non-IID split accuracies (Client A poses vs. Client B poses) from
 *      any 6x6 confusion matrix.
 *   2. `REFERENCE_CLIENT_A_MATRIX`, `REFERENCE_CLIENT_B_MATRIX`,
 *      `REFERENCE_GLOBAL_8BIT_MATRIX`, `REFERENCE_GLOBAL_32BIT_MATRIX`:
 *      60-sample universal evaluation dataset benchmarks (10 samples/pose across
 *      all 6 poses) demonstrating how isolated local models achieve ~96.7% on
 *      their 3 seen poses and 0% on the 3 unseen poses, while the Federated
 *      Global Model achieves high accuracy across all 6 poses.
 *   3. `simulateSymmetricQuantization(...)`: Exact TypeScript mirror of the
 *      symmetric min-max 8-bit quantization/dequantization math in Android
 *      `Quantizer.kt` and Spring Boot `QuantizedWeightsCodec.java`.
 * ============================================================================
 */
import {
  POSE_DEFINITIONS,
  type ClassMetrics,
  type ConfusionMatrix6x6,
  type ModelEvaluationSummary,
} from '../types/federated';

export function createEmptyConfusionMatrix(): ConfusionMatrix6x6 {
  return Array.from({ length: 6 }, () => Array(6).fill(0));
}

export function cloneConfusionMatrix(matrix: ConfusionMatrix6x6): ConfusionMatrix6x6 {
  return matrix.map((row) => [...row]);
}

/**
 * Computes Overall Accuracy, Per-Class Accuracy/Precision/Recall/F1, Macro-F1,
 * and Non-IID split accuracies (Client A primary poses vs Client B primary poses)
 * from a 6x6 confusion matrix where rows = True Pose (0..5) and cols = Predicted Pose (0..5).
 */
export function computeModelEvaluation(
  modelId: ModelEvaluationSummary['modelId'],
  title: string,
  subtitle: string,
  trainedOnDescription: string,
  confusionMatrix: ConfusionMatrix6x6
): ModelEvaluationSummary {
  let totalSamples = 0;
  let totalCorrect = 0;
  let clientASupport = 0;
  let clientACorrect = 0;
  let clientBSupport = 0;
  let clientBCorrect = 0;

  const perClass: ClassMetrics[] = POSE_DEFINITIONS.map((poseDef, i) => {
    const row = confusionMatrix[i] || [0, 0, 0, 0, 0, 0];
    const support = row.reduce((acc, v) => acc + v, 0);
    const correct = row[i] || 0;
    const colSum = confusionMatrix.reduce((acc, r) => acc + (r[i] || 0), 0);

    totalSamples += support;
    totalCorrect += correct;

    if (poseDef.primaryClient === 'CLIENT_A') {
      clientASupport += support;
      clientACorrect += correct;
    } else {
      clientBSupport += support;
      clientBCorrect += correct;
    }

    const recall = support > 0 ? (correct / support) * 100 : 0;
    const precision = colSum > 0 ? (correct / colSum) * 100 : 0;
    const f1 =
      precision + recall > 0 ? (2 * precision * recall) / (precision + recall) : 0;

    return {
      poseIndex: i,
      poseName: poseDef.name,
      primaryClient: poseDef.primaryClient,
      support,
      correct,
      accuracy: recall,
      precision,
      recall,
      f1,
    };
  });

  const activeClasses = perClass.filter((c) => c.support > 0);
  const denom = activeClasses.length > 0 ? activeClasses.length : 6;

  const macroPrecision =
    activeClasses.reduce((sum, c) => sum + c.precision, 0) / denom;
  const macroRecall =
    activeClasses.reduce((sum, c) => sum + c.recall, 0) / denom;
  const macroF1 = activeClasses.reduce((sum, c) => sum + c.f1, 0) / denom;

  return {
    modelId,
    title,
    subtitle,
    trainedOnDescription,
    confusionMatrix,
    overallAccuracy: totalSamples > 0 ? (totalCorrect / totalSamples) * 100 : 0,
    macroPrecision,
    macroRecall,
    macroF1,
    clientAPosesAccuracy:
      clientASupport > 0 ? (clientACorrect / clientASupport) * 100 : 0,
    clientBPosesAccuracy:
      clientBSupport > 0 ? (clientBCorrect / clientBSupport) * 100 : 0,
    perClass,
    totalSamples,
  };
}

/**
 * Reference Non-IID Experimental Matrices (10 universal calibration/test samples per pose = 60 total samples)
 * matching Phase 1 Section 3 Evaluation Plan:
 * - Client A Local Model: Trained exclusively on Screen Up (0), Screen Down (1), Portrait Up (2) [50 samples each]
 *   -> Near 100% on seen classes (0,1,2), 0% on unseen classes (3,4,5) because output weights for unseen classes are never positively reinforced.
 * - Client B Local Model: Trained exclusively on Portrait Down (3), Landscape Left (4), Landscape Right (5) [50 samples each]
 *   -> Near 100% on seen classes (3,4,5), 0% on unseen classes (0,1,2).
 * - Federated Global Model (8-bit Quantized FedAvg): Collaboratively aggregated across Client A + Client B
 *   -> High accuracy across all 6 spatial poses.
 */
export const REFERENCE_CLIENT_A_MATRIX: ConfusionMatrix6x6 = [
  // Pred:  SU, SD, PU, PD, LL, LR
  /* SU */ [10,  0,  0,  0,  0,  0],
  /* SD */ [ 0, 10,  0,  0,  0,  0],
  /* PU */ [ 1,  0,  9,  0,  0,  0],
  /* PD */ [ 2,  3,  5,  0,  0,  0], // Unseen by Client A -> misclassified into Client A's seen poses
  /* LL */ [ 4,  1,  5,  0,  0,  0], // Unseen by Client A -> misclassified into Client A's seen poses
  /* LR */ [ 3,  4,  3,  0,  0,  0], // Unseen by Client A -> misclassified into Client A's seen poses
];

export const REFERENCE_CLIENT_B_MATRIX: ConfusionMatrix6x6 = [
  // Pred:  SU, SD, PU, PD, LL, LR
  /* SU */ [ 0,  0,  0,  2,  4,  4], // Unseen by Client B -> misclassified into Client B's seen poses
  /* SD */ [ 0,  0,  0,  4,  3,  3], // Unseen by Client B -> misclassified into Client B's seen poses
  /* PU */ [ 0,  0,  0,  5,  2,  3], // Unseen by Client B -> misclassified into Client B's seen poses
  /* PD */ [ 0,  0,  0, 10,  0,  0],
  /* LL */ [ 0,  0,  0,  0,  9,  1],
  /* LR */ [ 0,  0,  0,  0,  0, 10],
];

export const REFERENCE_GLOBAL_8BIT_MATRIX: ConfusionMatrix6x6 = [
  // Pred:  SU, SD, PU, PD, LL, LR
  /* SU */ [10,  0,  0,  0,  0,  0],
  /* SD */ [ 0, 10,  0,  0,  0,  0],
  /* PU */ [ 0,  0,  9,  0,  1,  0],
  /* PD */ [ 0,  0,  0,  9,  0,  1],
  /* LL */ [ 1,  0,  0,  0,  9,  0],
  /* LR */ [ 0,  0,  0,  1,  0,  9],
];

export const REFERENCE_GLOBAL_32BIT_MATRIX: ConfusionMatrix6x6 = [
  // Pred:  SU, SD, PU, PD, LL, LR
  /* SU */ [10,  0,  0,  0,  0,  0],
  /* SD */ [ 0, 10,  0,  0,  0,  0],
  /* PU */ [ 0,  0, 10,  0,  0,  0],
  /* PD */ [ 0,  0,  0,  9,  0,  1],
  /* LL */ [ 1,  0,  0,  0,  9,  0],
  /* LR */ [ 0,  0,  0,  1,  0,  9],
];

/**
 * Mirrors Quantizer.kt and QuantizedWeightsCodec.java symmetric min-max 8-bit quantization.
 */
export interface QuantizationInspectionStep {
  index: number;
  layerLabel: string;
  originalFloat32: number;
  quantizedInt8: number;
  dequantizedFloat32: number;
  absError: number;
}

export function simulateSymmetricQuantization(weights: number[]): {
  maxAbs: number;
  scale: number;
  steps: QuantizationInspectionStep[];
  meanAbsoluteError: number;
  maxAbsoluteError: number;
} {
  let maxAbs = 0;
  for (const w of weights) {
    const absW = Math.abs(w);
    if (absW > maxAbs) maxAbs = absW;
  }
  if (maxAbs === 0) maxAbs = 1e-9;

  const encodeScale = 127.0 / maxAbs;
  const decodeScale = maxAbs / 127.0;

  const layerLabels = [
    'W1[0,0] (grav_mean_x → h0)',
    'W1[1,3] (grav_mean_y → h3)',
    'W1[2,7] (grav_mean_z → h7)',
    'b1[4] (hidden bias 4)',
    'W2[3,0] (h3 → Screen Up)',
    'W2[8,2] (h8 → Portrait Up)',
    'W2[12,4] (h12 → Landscape Left)',
    'b2[5] (output bias Landscape Right)',
  ];

  let totalError = 0;
  let maxError = 0;

  const steps: QuantizationInspectionStep[] = weights.map((w, idx) => {
    const scaled = Math.max(-127, Math.min(127, w * encodeScale));
    // Match Java/Kotlin (byte) truncation cast: Math.trunc(scaled)
    const qInt8 = Math.trunc(scaled);
    const deq = Math.fround(qInt8 * decodeScale);
    const absError = Math.abs(w - deq);
    totalError += absError;
    if (absError > maxError) maxError = absError;
    return {
      index: idx,
      layerLabel: layerLabels[idx] || `Weight[${idx}]`,
      originalFloat32: w,
      quantizedInt8: qInt8,
      dequantizedFloat32: deq,
      absError,
    };
  });

  return {
    maxAbs,
    scale: encodeScale,
    steps,
    meanAbsoluteError: steps.length > 0 ? totalError / steps.length : 0,
    maxAbsoluteError: maxError,
  };
}


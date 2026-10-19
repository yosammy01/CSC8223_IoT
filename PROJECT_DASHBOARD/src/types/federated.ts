/**
 * ============================================================================
 * FILE README: src/types/federated.ts
 * ============================================================================
 * Purpose:
 *   Centralized TypeScript domain types, interfaces, and architectural constants
 *   shared across PROJECT_DASHBOARD.
 *
 * Integration Alignment:
 *   - PoseName & POSE_DEFINITIONS: Matches the 6 spatial phone poses defined in
 *     `MainActivity.kt` (`PROJECT_ANDROID`) and Section 2 of the CSC 8223 spec
 *     (Screen Up, Screen Down, Portrait Up, Portrait Down, Landscape Left,
 *     Landscape Right) along with their Non-IID client split (Client A vs B).
 *   - TelemetryMessage: Matches the JSON payload published by Android
 *     `MqttClientManager.publishTelemetry()` to `federated/telemetry/{clientId}`
 *     and broadcast over WebSockets (`/ws/telemetry`) by `TelemetryWebSocketHandler`.
 *   - FederatedStatus & LatestMessageResponse: Matches the Java records returned
 *     by `MqttController.java` (`/api/mqtt/federated/status` & `/api/mqtt/latest`).
 *   - MLP & Quantization Constants: Matches `MultiLayerPerceptron.kt` (24 inputs ->
 *     16 hidden -> 6 outputs = 502 total float weights = 2,008 bytes uncompressed)
 *     and `Quantizer.kt` / `QuantizedWeightsCodec.java` (4-byte maxAbs header +
 *     502 signed int8 bytes = 506 bytes quantized, a 74.80% payload reduction).
 * ============================================================================
 */
export type PoseName =
  | 'Screen Up'
  | 'Screen Down'
  | 'Portrait Up'
  | 'Portrait Down'
  | 'Landscape Left'
  | 'Landscape Right';

export type ActiveModelType = 'LOCAL' | 'GLOBAL';

export type ClientRole = 'CLIENT_A' | 'CLIENT_B';

export interface PoseDefinition {
  index: number;
  name: PoseName;
  shortName: string;
  definition: string;
  primaryClient: ClientRole;
  gravityHint: string;
  rotationStyle: string;
}

export interface ClientDatasetStatus {
  trainCounts: number[]; // length 6 (0..50 samples per pose)
  calibCounts: number[]; // length 6 (0..10 samples per pose)
  localAccuracy: number | null;
  globalAccuracy: number | null;
}

export interface TelemetryMessage {
  clientId: string;
  pose: string;
  timestamp: number;
  activeModel?: ActiveModelType;
  confidence?: number;
  trainCounts?: number[];
  calibCounts?: number[];
  evalCounts?: number[];
  localAccuracy?: number | null;
  globalAccuracy?: number | null;
}

export interface FederatedStatus {
  receivedUpdates: number;
  completedRounds: number;
  pendingUpdates: number;
  expectedClients: number;
  globalWeightCount: number;
  lastAggregationAt: string | null;
}

export interface ReceivedMqttMessage {
  topic: string;
  payload: string;
  qos: number;
  retained: boolean;
  receivedAt: string;
}

export interface LatestMessageResponse {
  message: ReceivedMqttMessage | null;
  timestamp: string;
}

export interface ApiHealthResponse {
  status: string;
  timestamp: string;
}

export interface ClassMetrics {
  poseIndex: number;
  poseName: PoseName;
  primaryClient: ClientRole;
  support: number;
  correct: number;
  accuracy: number; // 0..100
  precision: number; // 0..100
  recall: number; // 0..100
  f1: number; // 0..100
}

export type ConfusionMatrix6x6 = number[][];

export interface ModelEvaluationSummary {
  modelId: 'CLIENT_A_LOCAL' | 'CLIENT_B_LOCAL' | 'FEDERATED_GLOBAL_8BIT' | 'FEDERATED_GLOBAL_32BIT';
  title: string;
  subtitle: string;
  trainedOnDescription: string;
  confusionMatrix: ConfusionMatrix6x6;
  overallAccuracy: number; // 0..100
  macroPrecision: number; // 0..100
  macroRecall: number; // 0..100
  macroF1: number; // 0..100
  clientAPosesAccuracy: number; // 0..100 (Screen Up, Screen Down, Portrait Up)
  clientBPosesAccuracy: number; // 0..100 (Portrait Down, Landscape Left, Landscape Right)
  perClass: ClassMetrics[];
  totalSamples: number;
}

export interface ActivityLogEntry {
  id: string;
  timestamp: string;
  category: 'COMMAND' | 'TELEMETRY' | 'AGGREGATION' | 'SYSTEM';
  summary: string;
  detail?: string;
}

export const POSE_NAMES: PoseName[] = [
  'Screen Up',
  'Screen Down',
  'Portrait Up',
  'Portrait Down',
  'Landscape Left',
  'Landscape Right',
];

export const POSE_DEFINITIONS: PoseDefinition[] = [
  {
    index: 0,
    name: 'Screen Up',
    shortName: 'Screen Up',
    definition: 'Phone lies flat with the screen facing upward.',
    primaryClient: 'CLIENT_A',
    gravityHint: 'g_z ≈ +9.8 m/s² ( +1.0g )',
    rotationStyle: 'rotateX(55deg) rotateZ(0deg)',
  },
  {
    index: 1,
    name: 'Screen Down',
    shortName: 'Screen Down',
    definition: 'Phone lies flat with the screen facing downward.',
    primaryClient: 'CLIENT_A',
    gravityHint: 'g_z ≈ -9.8 m/s² ( -1.0g )',
    rotationStyle: 'rotateX(-125deg) rotateZ(0deg)',
  },
  {
    index: 2,
    name: 'Portrait Up',
    shortName: 'Portrait Up',
    definition: 'Phone is vertical with the top edge pointing upward.',
    primaryClient: 'CLIENT_A',
    gravityHint: 'g_y ≈ +9.8 m/s² ( +1.0g )',
    rotationStyle: 'rotateX(0deg) rotateZ(0deg)',
  },
  {
    index: 3,
    name: 'Portrait Down',
    shortName: 'Portrait Down',
    definition: 'Phone is vertical with the top edge pointing downward.',
    primaryClient: 'CLIENT_B',
    gravityHint: 'g_y ≈ -9.8 m/s² ( -1.0g )',
    rotationStyle: 'rotateX(0deg) rotateZ(180deg)',
  },
  {
    index: 4,
    name: 'Landscape Left',
    shortName: 'Landscape Left',
    definition:
      'The top of the phone points to the left, and the right edge points upward.',
    primaryClient: 'CLIENT_B',
    gravityHint: 'g_x ≈ +9.8 m/s² ( +1.0g )',
    rotationStyle: 'rotateX(0deg) rotateZ(-90deg)',
  },
  {
    index: 5,
    name: 'Landscape Right',
    shortName: 'Landscape Right',
    definition:
      'The top of the phone points to the right, and the left edge points upward.',
    primaryClient: 'CLIENT_B',
    gravityHint: 'g_x ≈ -9.8 m/s² ( -1.0g )',
    rotationStyle: 'rotateX(0deg) rotateZ(90deg)',
  },
];

// Constants matching MultiLayerPerceptron.kt, Quantizer.kt, and QuantizedWeightsCodec.java
export const MLP_INPUT_FEATURES = 24; // 8 Gravity + 8 Accelerometer + 8 Gyroscope features
export const MLP_HIDDEN_NEURONS = 16;
export const MLP_OUTPUT_CLASSES = 6;
export const MLP_LEARNING_RATE = 0.1;
export const MLP_TOTAL_WEIGHTS =
  MLP_INPUT_FEATURES * MLP_HIDDEN_NEURONS +
  MLP_HIDDEN_NEURONS +
  MLP_HIDDEN_NEURONS * MLP_OUTPUT_CLASSES +
  MLP_OUTPUT_CLASSES; // 384 + 16 + 96 + 6 = 502 floats

export const FLOAT32_PAYLOAD_BYTES = MLP_TOTAL_WEIGHTS * 4; // 2,008 bytes
export const INT8_QUANTIZED_PAYLOAD_BYTES = 4 + MLP_TOTAL_WEIGHTS; // 4-byte maxAbs header + 502 int8 bytes = 506 bytes
export const PAYLOAD_REDUCTION_PERCENT =
  ((FLOAT32_PAYLOAD_BYTES - INT8_QUANTIZED_PAYLOAD_BYTES) / FLOAT32_PAYLOAD_BYTES) * 100; // 74.8008%
export const COMPRESSION_RATIO = FLOAT32_PAYLOAD_BYTES / INT8_QUANTIZED_PAYLOAD_BYTES; // 3.9684x


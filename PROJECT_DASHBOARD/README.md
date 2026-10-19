# CSC 8223 Course Project — Federated Learning Smartphone Pose Dashboard (`PROJECT_DASHBOARD`)

> **Core Question**: *"Can smart devices with different (non-IID) local sensor data collaboratively learn a global model that performs better than any device can learn alone?"*

This folder (`PROJECT_DASHBOARD`) contains the **Visualization & Operator Control Tier** for the **Federated Learning with Smartphone Sensors** course project. Built with **React 19**, **TypeScript**, **Vite**, and **Tailwind CSS v4**, the dashboard integrates directly with the **Spring Boot Orchestration Server** (`PROJECT_BACKEND`) and the **Android IoT Edge Clients** (`PROJECT_ANDROID`) via WebSockets and REST-to-MQTT command bridges.

---

## Table of Contents

1. [End-to-End Multi-Tier System Architecture](#1-end-to-end-multi-tier-system-architecture)
2. [Six-Pose Classification & Non-IID Data Distribution](#2-six-pose-classification--non-iid-data-distribution)
3. [On-Device Local Learning & MLP Specification](#3-on-device-local-learning--mlp-specification)
4. [Technical Contribution: 8-Bit Symmetric Min-Max Weight Quantization](#4-technical-contribution-8-bit-symmetric-min-max-weight-quantization)
5. [Three-Model Evaluation & Metrics Suite](#5-three-model-evaluation--metrics-suite)
6. [Backend REST, WebSocket & MQTT Integration Contract](#6-backend-rest-websocket--mqtt-integration-contract)
7. [Codebase File-by-File Guide](#7-codebase-file-by-file-guide)
8. [Required Software, Build & Run Instructions](#8-required-software-build--run-instructions)
9. [In-Class Demonstration Walkthrough](#9-in-class-demonstration-walkthrough)

---

## 1. End-to-End Multi-Tier System Architecture

The system is partitioned into three tiers that strictly enforce the **Federated Learning Privacy Rule** (*raw sensor measurements and extracted feature vectors never leave the smartphones*):

1. **Edge Tier (`PROJECT_ANDROID`)**:
   - Two Android smartphones run `SensorService.kt` as a foreground service with locked portrait orientation.
   - `FeatureExtractor.kt` samples 3-axis gravity (`Sensor.TYPE_GRAVITY`, with `Sensor.TYPE_ACCELEROMETER` fallback) over a **100 ms sliding window** and extracts **8 normalized features** ($\mu_x, \mu_y, \mu_z, \sigma_x, \sigma_y, \sigma_z, \mu_{\|\mathbf{g}\|}, \sigma_{\|\mathbf{g}\|}$ divided by $9.8\text{ m/s}^2$).
   - `ModelManager.kt` maintains two separate models (`MultiLayerPerceptron.kt`) in memory:
     - `localModel`: Trained strictly on the device's isolated non-IID training dataset.
     - `federatedModel`: Updated with global weights aggregated by the server.
   - `MqttClientManager.kt` publishes quantized local weight updates to `federated/local/weights/{clientId}` and live pose predictions to `federated/telemetry/{clientId}`.

2. **Orchestration Tier (`PROJECT_BACKEND` + Eclipse Mosquitto)**:
   - `MqttIntegrationConfig.java` subscribes to `federated/local/weights/#` and `federated/telemetry/#`.
   - `FederatedAggregationService.java` decodes incoming quantized client updates via `QuantizedWeightsCodec.java`, executes **Federated Averaging (FedAvg)** once `expectedClients` (2) updates arrive, persists snapshots via `FederatedModelSnapshotRepository`, and broadcasts the updated global model to `federated/global/weights`.
   - `TelemetryWebSocketHandler.java` broadcasts live edge pose telemetry to connected web clients over `/ws/telemetry`.

3. **Visualization & Control Tier (`PROJECT_DASHBOARD`)**:
   - Streams live pose telemetry from `/ws/telemetry` and renders real-time 3D-styled spatial orientation cards for **Client A** and **Client B**.
   - Polls `/api/mqtt/federated/status` and `/api/mqtt/latest` to track completed FedAvg rounds, received client updates, and round barrier synchronization.
   - Dispatches remote MQTT control payloads (`START_TRAINING`, `SET_MODEL`, `RESET_GLOBAL`, `RESET_ALL`) and model snapshot sync requests (`/api/mqtt/federated/model/republish` & `/republish-all`).
   - Computes and visualizes **Overall Accuracy**, **Per-Class Accuracy & F1**, **Macro-F1 Score**, **$6 \times 6$ Confusion Matrices**, and **8-Bit Quantization Payload Reduction**.

---

## 2. Six-Pose Classification & Non-IID Data Distribution

Neither client sees the complete 6-pose classification problem during local training:

| Index | Pose Label | Physical Orientation Definition | Dominant Gravity Axis | Client A Local Train | Client B Local Train | Universal Calibration / Eval |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: |
| `0` | **Screen Up** | Phone lies flat with the screen facing upward. | $g_z \approx +9.8\text{ m/s}^2$ | **50 samples** | 0 *(unseen)* | 10 samples / phone |
| `1` | **Screen Down** | Phone lies flat with the screen facing downward. | $g_z \approx -9.8\text{ m/s}^2$ | **50 samples** | 0 *(unseen)* | 10 samples / phone |
| `2` | **Portrait Up** | Phone is vertical with the top edge pointing upward. | $g_y \approx +9.8\text{ m/s}^2$ | **50 samples** | 0 *(unseen)* | 10 samples / phone |
| `3` | **Portrait Down** | Phone is vertical with the top edge pointing downward. | $g_y \approx -9.8\text{ m/s}^2$ | 0 *(unseen)* | **50 samples** | 10 samples / phone |
| `4` | **Landscape Left** | Phone is vertical with the left edge pointing upward. | $g_x \approx -9.8\text{ m/s}^2$ | 0 *(unseen)* | **50 samples** | 10 samples / phone |
| `5` | **Landscape Right** | Phone is vertical with the right edge pointing upward. | $g_x \approx +9.8\text{ m/s}^2$ | 0 *(unseen)* | **50 samples** | 10 samples / phone |

---

## 3. On-Device Local Learning & MLP Specification

Matching `FeatureExtractor.kt` and `MultiLayerPerceptron.kt` in `PROJECT_ANDROID`:

- **Sliding Window Length**: `100 ms` non-overlapping window of 3-axis gravity samples.
- **Input Layer ($8$ features)**:
  $$\mathbf{x} = \frac{1}{9.8}\left[\mu_x, \mu_y, \mu_z, \sigma_x, \sigma_y, \sigma_z, \mu_{\|\mathbf{g}\|}, \sigma_{\|\mathbf{g}\|}\right] \in \mathbb{R}^8$$
- **Hidden Layer ($16$ neurons)**: Fully connected layer with Sigmoid activation $\sigma(z) = \frac{1}{1 + e^{-z}}$.
  - Weights $W_1 \in \mathbb{R}^{8 \times 16}$ ($128$ floats), Biases $b_1 \in \mathbb{R}^{16}$ ($16$ floats).
- **Output Layer ($6$ classes)**: Fully connected layer with numerically stabilized Softmax activation.
  - Weights $W_2 \in \mathbb{R}^{16 \times 6}$ ($96$ floats), Biases $b_2 \in \mathbb{R}^6$ ($6$ floats).
- **Total Trainable Parameters ($N$)**:
  $$N = 128 + 16 + 96 + 6 = 246\text{ floats}$$
- **Training Rule**: Cross-entropy loss with stochastic gradient descent ($\eta = 0.1$), publishing quantized weight updates every 10 recorded samples and running 20 epochs over the local dataset upon pose completion.

---

## 4. Technical Contribution: 8-Bit Symmetric Min-Max Weight Quantization

Standard Federated Averaging transmits dense 32-bit IEEE-754 floating-point weight arrays ($246 \times 4\text{ bytes} = 984\text{ bytes}$ per update). To reduce communication overhead on mobile IoT clients, `Quantizer.kt` and `QuantizedWeightsCodec.java` implement **8-Bit Symmetric Min-Max Quantization**:

1. **On-Device Quantization (`Quantizer.quantize`)**:
   - Computes $\text{maxAbs} = \max_{i} |w_i|$ (with $10^{-9}$ floor to prevent division by zero).
   - Computes scaling factor $s = \frac{127.0}{\text{maxAbs}}$.
   - Clamps and casts each weight into a signed 8-bit integer:
     $$q_i = \text{clamp}(\text{trunc}(w_i \cdot s), -127, 127) \in [-127, 127]$$
   - Packs a **250-byte binary MQTT payload**:
     - `Bytes [0..3]`: 4-byte Big-Endian IEEE-754 float `maxAbs`.
     - `Bytes [4..249]`: 246 signed `int8` weight values.
2. **Server Dequantization & Aggregation (`QuantizedWeightsCodec.decode` & `encode`)**:
   - Reconstructs $\hat{w}_i = q_i \cdot \frac{\text{maxAbs}}{127.0}$, averages updates across clients via FedAvg, and re-encodes the aggregated global model into the 250-byte wire format.
3. **Payload Efficiency**:
   - **32-Bit Baseline Payload**: `984 Bytes`
   - **8-Bit Quantized Payload**: `250 Bytes`
   - **Payload Size Reduction**: **74.59%** ($3.94\times$ compression ratio), saving `734 Bytes` per model transmission (`2,202 Bytes` saved per 2-client FedAvg round).

---

## 5. Three-Model Evaluation & Metrics Suite

The dashboard evaluates and compares the three required models on the universal 6-pose test set (`10 samples/pose = 60 samples`):

1. **Client A Local Model** — achieves near 100% accuracy on `Screen Up`, `Screen Down`, and `Portrait Up`, but **0% accuracy** on Client B's three poses (`~48.3%` overall accuracy).
2. **Client B Local Model** — achieves near 100% accuracy on `Portrait Down`, `Landscape Left`, and `Landscape Right`, but **0% accuracy** on Client A's three poses (`~48.3%` overall accuracy).
3. **Federated Global Model (8-Bit Quantized & 32-Bit Baseline)** — achieves **>93% overall accuracy and Macro-F1** across all six poses by aggregating knowledge from both non-IID clients without exchanging raw sensor data.

The dashboard supports both:
- **Reference Benchmark Mode**: Pre-loaded $6 \times 6$ confusion matrices from the universal 60-sample evaluation dataset.
- **Interactive / Live Capture Mode**: Allows recording live WebSocket predictions from Client A or Client B against a selected ground-truth pose during the in-class demonstration, dynamically recomputing confusion matrices, per-class recall/F1, and Macro-F1 in real time.

---

## 6. Backend REST, WebSocket & MQTT Integration Contract

| Protocol | Endpoint / Topic | Direction | Payload Format & Purpose |
| :--- | :--- | :--- | :--- |
| **WebSocket** | `ws://localhost:8080/ws/telemetry` | Server → Dashboard | `{"clientId":"EdgeClient_A","pose":"Screen Up","timestamp":1729340000000}` |
| **REST GET** | `/api/mqtt/federated/status` | Server → Dashboard | `{"receivedUpdates":4,"completedRounds":2,"pendingUpdates":0,"expectedClients":2,"globalWeightCount":246,"lastAggregationAt":"..."}` |
| **REST GET** | `/api/mqtt/latest` | Server → Dashboard | Latest MQTT message captured by `MqttMessageStore`. |
| **REST POST** | `/api/mqtt/federated/training` | Dashboard → Server → MQTT | `{"useQuantization":true}` → publishes `{"command":"START_TRAINING","use_quantization":true}` to `federated/commands`. |
| **REST POST** | `/api/mqtt/federated/model/toggle` | Dashboard → Server → MQTT | `{"activeModel":"GLOBAL"}` → publishes `{"command":"SET_MODEL","active_model":"GLOBAL"}` to `federated/commands`. |
| **REST POST** | `/api/mqtt/federated/model/republish` | Dashboard → Server → MQTT | Republishes latest global weights to `federated/global/weights`. |
| **REST POST** | `/api/mqtt/federated/model/republish-all` | Dashboard → Server → MQTT | Republishes global weights + per-client local weights (`federated/local/weights/{clientId}`). |
| **REST POST** | `/api/mqtt/federated/command` | Dashboard → Server → MQTT | Publishes JSON commands (`RESET_GLOBAL`, `RESET_ALL`, `START_TRAINING`, `SET_MODEL`) to `federated/commands`. |

---

## 7. Codebase File-by-File Guide

Every code file in `PROJECT_DASHBOARD` includes a top-of-file `FILE README` header explaining its role:

- [`index.html`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/index.html): HTML entry template mounting `#root`.
- [`vite.config.ts`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/vite.config.ts): Vite configuration proxying `/api` (`http://localhost:8080`) and `/ws` (`ws://localhost:8080`) to Spring Boot.
- [`postcss.config.js`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/postcss.config.js) & [`tailwind.config.js`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/tailwind.config.js): Tailwind CSS v4 (`@tailwindcss/postcss`) configuration.
- [`eslint.config.js`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/eslint.config.js): Flat ESLint config for TypeScript and React Hooks.
- [`src/main.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/main.tsx): React DOM entry point mounting `<App />`.
- [`src/index.css`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/index.css): Global stylesheet loading `@import "tailwindcss";`.
- [`src/types/federated.ts`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/types/federated.ts): Shared TypeScript interfaces, pose definitions, and MLP/quantization constants (`246` weights, `984 B` vs `250 B`).
- [`src/data/evaluationData.ts`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/data/evaluationData.ts): Confusion matrix metric engine (Overall Accuracy, Per-Class Precision/Recall/F1, Macro-F1), reference non-IID matrices, and symmetric 8-bit quantization simulator.
- [`src/App.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/App.tsx): Root application state manager handling WebSocket telemetry, REST status polling, MQTT command dispatch, and section navigation.
- [`src/components/PhonePoseVisualizer.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/components/PhonePoseVisualizer.tsx): Live Client A & Client B 3D spatial pose cards, non-IID in-distribution vs. out-of-distribution badges, device ID binding, and demo telemetry simulator.
- [`src/components/OrchestrationControlPanel.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/components/OrchestrationControlPanel.tsx): Remote experiment controls (`START_TRAINING` 32-bit vs. 8-bit, `SET_MODEL` `LOCAL` vs. `GLOBAL`, snapshot sync, edge resets) and live FedAvg round status monitor.
- [`src/components/ModelComparisonSection.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/components/ModelComparisonSection.tsx): Side-by-side 3-model comparison table, per-pose accuracy bars, $6 \times 6$ confusion matrices, live telemetry evaluation capture tool, and Android on-device benchmark logger.
- [`src/components/QuantizationContributionSection.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/components/QuantizationContributionSection.tsx): 8-bit symmetric min-max quantization wire layout visualization, cumulative MQTT bandwidth calculator, and interactive weight codec inspector.
- [`src/components/SystemArchitecturePipeline.tsx`](file:///c:/Users/samue/source/repos/CSC8223_IoT/PROJECT_DASHBOARD/src/components/SystemArchitecturePipeline.tsx): 5-step FL workflow diagram, non-IID dataset specification table, on-device MLP engineering summary, and Privacy Rule compliance banner.

---

## 8. Required Software, Build & Run Instructions

### Required Systems & Software Versions
- **Node.js**: `v20.x` or `v22.x` LTS (with `npm`)
- **React / Vite / Tailwind**: React `^19.2.8`, Vite `^8.2.0`, Tailwind CSS `^4.3.3`, TypeScript `~6.0.2`
- **Companion Services** (for live hardware mode):
  - Eclipse Mosquitto MQTT Broker (`tcp://localhost:1883`)
  - Spring Boot Backend (`PROJECT_BACKEND` on `http://localhost:8080`, Java 21+)
  - Two Android Devices running `PROJECT_ANDROID` (Android 8.0+ / API 26+)

### Step-by-Step Build & Run Commands

```powershell
# 1. Navigate to the PROJECT_DASHBOARD folder
cd PROJECT_DASHBOARD

# 2. Install dependencies
npm install

# 3. Start the Vite development server (http://localhost:5173)
#    Automatically proxies /api -> http://localhost:8080 and /ws -> ws://localhost:8080
npm run dev

# 4. Run TypeScript type-checking and production bundle build
npm run build

# 5. Run ESLint verification
npm run lint
```

---

## 9. In-Class Demonstration Walkthrough

1. **Connect Edge Devices**: Launch the Android app on Phone A and Phone B, enter the broker IP, and tap **Connect**. Both devices appear in **Live Edge Device Spatial Pose Telemetry** and can be bound to **Client A** and **Client B**.
2. **Demonstrate Isolated Local Model Blindness (Non-IID)**:
   - Click **Switch to Local Model** (`{"command":"SET_MODEL","active_model":"LOCAL"}`).
   - Place Phone A in `Screen Up`, `Screen Down`, and `Portrait Up` (in-distribution → accurate predictions), then rotate Phone A to `Portrait Down` or `Landscape Left` (out-of-distribution → badge warns `0 samples on Client A` and isolated local model misclassifies).
3. **Demonstrate Collaborative Federated Global Model**:
   - Click **Start Training (8-bit Quantized)** or **Republish Global** and watch **FedAvg Aggregation Status** increment `Completed FedAvg Rounds`.
   - Click **Switch to Global Model** (`{"command":"SET_MODEL","active_model":"GLOBAL"}`).
   - Test both phones across all 6 poses (`Screen Up`, `Screen Down`, `Portrait Up`, `Portrait Down`, `Landscape Left`, `Landscape Right`) to show accurate recognition across the entire domain.
4. **Present Quantitative Evaluation & Technical Contribution**:
   - Open **2. Three-Model Comparison (6×6 Matrices)** to show the confusion matrices, Macro-F1 scores, and live prediction capture.
   - Open **3. 8-Bit Quantization Contribution** to show the `984 B` $\rightarrow$ `250 B` (**74.59%**) MQTT payload reduction and inspect individual quantized weights.

---

## 9. In-Class Demonstration Walkthrough

1. **Connect Edge Devices**: Launch the Android app on Phone A and Phone B, enter the broker IP, and tap **Connect**. Both devices appear in **Live Edge Device Spatial Pose Telemetry** and can be bound to **Client A** and **Client B**.
2. **Demonstrate Isolated Local Model Blindness (Non-IID)**:
   - Click **Switch to Local Model** (`{"command":"SET_MODEL","active_model":"LOCAL"}`).
   - Place Phone A in `Screen Up`, `Screen Down`, and `Portrait Up` (in-distribution → accurate predictions), then rotate Phone A to `Portrait Down` or `Landscape Left` (out-of-distribution → badge warns `0 samples on Client A` and isolated local model misclassifies).
3. **Demonstrate Collaborative Federated Global Model**:
   - Click **Start Training (8-bit Quantized)** or **Republish Global** and watch **FedAvg Aggregation Status** increment `Completed FedAvg Rounds`.
   - Click **Switch to Global Model** (`{"command":"SET_MODEL","active_model":"GLOBAL"}`).
   - Test both phones across all 6 poses (`Screen Up`, `Screen Down`, `Portrait Up`, `Portrait Down`, `Landscape Left`, `Landscape Right`) to show accurate recognition across the entire domain.
4. **Present Quantitative Evaluation & Technical Contribution**:
   - Open **2. Three-Model Comparison (6×6 Matrices)** to show the confusion matrices, Macro-F1 scores, and live prediction capture.
   - Open **3. 8-Bit Quantization Contribution** to show the `984 B` $\rightarrow$ `250 B` (**74.59%**) MQTT payload reduction and inspect individual quantized weights.


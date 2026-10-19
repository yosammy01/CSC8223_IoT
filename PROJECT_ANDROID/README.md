# CSC 8223 Course Project: Federated Learning with Smartphone Sensors
**Author:** Samuel Robles

This Android application serves as the Edge Client for a complete Federated Learning (FL) pipeline. It acts as an independent IoT sensor node that collects motion data, trains a neural network locally, and coordinates with a Spring Boot backend server over MQTT to build a global classification model without ever transmitting raw data.

## Project Compliance Checklist
* ✅ **Two Android Clients:** Can run on multiple phones identifying via unique `EdgeClient_` IDs.
* ✅ **Raw Data Privacy:** Raw `SensorEvent` data is processed in a 100ms sliding window and discarded immediately. It never leaves the device.
* ✅ **Non-IID Datasets:** The UI allows targeting distinct poses to easily simulate a split environment (e.g., Client A records Poses 0,1,2 while Client B records Poses 3,4,5).
* ✅ **Two Local Models & One Global Model:** Maintains isolated `localModel` and `federatedModel` states.
* ✅ **Evaluation & Calibration:** Includes an "Eval / Calib" tab to collect a universal testing set and benchmark Local vs Global Accuracy.
* ✅ **Meaningful Technical Contribution:** Implements **8-bit Weight Quantization** to reduce communication payloads by 75%, alongside a 32-bit raw fallback mode to benchmark the efficiency gain.

---

## Architecture Overview

### 1. The Sensor Pipeline (`SensorService.kt` & `FeatureExtractor.kt`)
Android's `SensorManager` runs as a Foreground Service, ensuring it never sleeps. It collects Gravity/Accelerometer data and feeds it into a **100-millisecond sliding window**. The window extracts an 8-element feature array (Mean X/Y/Z, StdDev X/Y/Z, Mean Mag, StdDev Mag) normalized by 9.8.

### 2. The Machine Learning Model (`MultiLayerPerceptron.kt` & `ModelManager.kt`)
A natively written Kotlin Neural Network consisting of 8 inputs, 16 hidden neurons (Sigmoid), and 6 output neurons (Softmax). It contains exactly 246 float parameters. Multi-epoch Stochastic Gradient Descent (SGD) allows the model to converge rapidly on the mobile CPU.

### 3. Federated Orchestration (`MqttClientManager.kt` & `Quantizer.kt`)
The app utilizes Eclipse Paho to maintain a persistent connection to a Mosquitto MQTT Broker. 
* It publishes its local weights upon completion of training (`federated/local/weights/{clientId}`).
* It listens to server commands (`federated/commands`) to trigger actions from a web dashboard.
* It subscribes to `federated/global/weights` to seamlessly apply aggregated server weights to the Global model.

---

## How to Setup and Run the Experiment

### Prerequisites
1. Ensure your PC running the Spring Boot server and Mosquitto broker is on the **same Wi-Fi network** as the phones.
2. Ensure your `mosquitto.conf` contains:
   ```text
   listener 1883 0.0.0.0
   allow_anonymous true
   ```
3. Allow **Port 1883 TCP Inbound** in Windows Defender Firewall.

### Step-by-Step Execution
1. **Launch the App:** Open the app on both Phone A and Phone B.
2. **Connect to Server:** In the "Broker IP" field at the top, enter your PC's Wi-Fi IP address (e.g., `192.168.1.237`) and tap **Connect**.
3. **Train Non-IID Local Data:**
   - Go to the **Training** tab on Phone A. Record 50 samples for *Screen Up*, *Screen Down*, and *Portrait Up*.
   - Go to the **Training** tab on Phone B. Record 50 samples for *Portrait Down*, *Landscape Left*, and *Landscape Right*.
   - *Note: As training finishes, local weights are automatically published to the server.*
4. **Collect Evaluation Data:**
   - Go to the **Eval / Calib** tab on *both* phones.
   - Record the small 10-sample calibration set for **all 6 poses**.
5. **Run the Benchmark:**
   - With the server having aggregated the Global Model, tap **Run Evaluation**.
   - You will see the **Local Model Accuracy** remain low (~50%) while the **Global Model Accuracy** reaches near perfect accuracy (~98%+), proving the effectiveness of the Federated Learning loop.
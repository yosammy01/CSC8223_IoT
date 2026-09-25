# Sensor Collector - Accelerometer Data Collector

**Sensor Collector** is an Android application designed to collect high-precision accelerometer sensor data for physical activity analysis (such as **Standing Still** and **Walking**) and export the recordings as standardized CSV files.

---

## 🚀 Features

- **Activity Labeling**: Quick selection between **Standing Still**, **Walking**, or custom activity labels (e.g., Running, Sitting).
- **Sampling Frequency Control**: Choose between **50 Hz** (default for Human Activity Recognition), **100 Hz** (fastest), or **20 Hz** (UI rate).
- **Real-time Monitoring**: Displays live 3-axis ($x, y, z$) acceleration values, magnitude ($\sqrt{x^2 + y^2 + z^2}$), elapsed timer, and collected sample counts.
- **Background Foreground Service**: Uses `SensorRecordingService` with `WakeLock` to ensure continuous data collection without missing samples even if the screen turns off during walking/standing tests.
- **CSV Export & Storage**:
  - Save CSV files directly to device folders (e.g., *Downloads*) via Android Storage Access Framework.
  - Share CSV files via Android Share Sheet (Email, Google Drive, Messaging, etc.) using `FileProvider`.
  - **Export All (ZIP)**: Compress and export all recorded CSV sessions into a single `.zip` file.
- **Recording History**: Review saved session metadata including duration, sample count, timestamp, and file size.

---

## 📊 CSV File Structure

Each recording generates a CSV file named using the pattern `accel_<activity_label>_<timestamp>.csv` (e.g., `accel_standing_still_20250304_120000.csv`).

```csv
timestamp_ms,rel_timestamp_ms,accel_x,accel_y,accel_z,label
1710000000000,0,0.1234,0.4567,9.8100,Standing Still
1710000000020,20,0.1500,0.4800,9.7900,Standing Still
1710000000040,40,0.1100,0.4200,9.8200,Standing Still
```

### Fields:
- `timestamp_ms`: Epoch timestamp in milliseconds (`System.currentTimeMillis()`).
- `rel_timestamp_ms`: Elapsed time in milliseconds since recording start.
- `accel_x`: Linear + gravity acceleration along the X-axis ($m/s^2$).
- `accel_y`: Linear + gravity acceleration along the Y-axis ($m/s^2$).
- `accel_z`: Linear + gravity acceleration along the Z-axis ($m/s^2$).
- `label`: Activity mode associated with the recording session (*Standing Still*, *Walking*, etc.).

---

## 🏗 Architecture & Key Components

- **`MainActivity.kt`**: Binds to recording service, manages UI state, permissions (Android 13+ notifications), and file export dialogs.
- **`SensorRecordingService.kt`**: Android Foreground Service listening to `SensorManager.TYPE_ACCELEROMETER`, streaming data directly to disk.
- **`CsvExporter.kt`**: Utility for CSV file creation, metadata parsing, ZIP compression, and `FileProvider` URI generation.
- **`RecordingRepository.kt`**: Asynchronous manager for loading and managing local recording files.
- **`RecordingAdapter.kt`**: RecyclerView adapter for rendering saved recordings list with action buttons.
- **`SensorCollectorUnitTest.kt`**: Local unit tests verifying CSV line generation and session file parsing.

---

## 🧪 Testing & Building

Run the unit tests using Gradle:
```bash
./gradlew test
```

Build the APK:
```bash
./gradlew assembleDebug
```

# JAVA_BACKEND

Spring Boot orchestration server for the Android federated pose clients. The
backend coordinates MQTT communication, aggregates model updates, persists model
snapshots, exposes operator REST endpoints, and forwards device telemetry over
WebSockets. Sensor collection, local training, and prediction remain on the
Android devices.

## Requirements

- Java 26
- Maven (the included `mvnw.cmd` wrapper is recommended)
- PostgreSQL
- Eclipse Mosquitto or another MQTT 3-compatible broker

The default database is `postgres` on `localhost:5432`, and the default MQTT
broker is `tcp://localhost:1883`. Override these values with the environment
variables documented below.

## Start the dependencies

The included `compose.yaml` starts PostgreSQL:

```powershell
docker compose up -d postgres
```

Start Mosquitto separately if it is not already running. The backend does not
start the MQTT broker.

## Build, test, and run

From this directory:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The server listens on `http://localhost:8080` by default. Check its health:

```powershell
curl.exe http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP","timestamp":"..."}
```

## Architecture

1. `MqttIntegrationConfig` creates the MQTT publisher and subscriber channels.
2. Incoming local model payloads are decoded by `QuantizedWeightsCodec`.
3. `FederatedAggregationService` caches local models, performs equal-weight
   FedAvg after the configured number of clients have submitted updates, stores
   snapshots, and publishes the global model.
4. `MqttController` exposes REST controls for publishing commands and viewing
   federated status.
5. `TelemetryWebSocketHandler` broadcasts device telemetry to dashboard clients.
6. JPA stores local and global model snapshots in PostgreSQL.

Raw sensor samples and extracted training examples are not sent to this server.
The server receives model-update bytes and the telemetry needed by the dashboard.

## MQTT topics and payloads

| Purpose | Default topic |
|---|---|
| Generic publish | `java_backend/default` |
| Local model updates | `federated/local/weights/<clientId>` |
| Aggregated global model | `federated/global/weights` |
| Device commands | `federated/commands` |
| Device telemetry | `federated/telemetry/<clientId>` |

Local and global model payloads use the Android-compatible format: a 4-byte
big-endian `maxAbs` float followed by signed 8-bit quantized weights. The
backend averages the dequantized values and re-encodes the result.

## REST API

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api` | Basic application information |
| `GET` | `/api/health` | Health status |
| `POST` | `/api/mqtt/publish` | Publish a generic MQTT payload |
| `GET` | `/api/mqtt/latest` | Read the latest received MQTT message |
| `POST` | `/api/mqtt/federated/command` | Publish a JSON command |
| `GET` | `/api/mqtt/federated/status` | Read aggregation counters and model state |
| `POST` | `/api/mqtt/federated/model/toggle` | Send `LOCAL` or `GLOBAL` model selection |
| `POST` | `/api/mqtt/federated/model/republish` | Republish the latest global model |
| `POST` | `/api/mqtt/federated/model/republish-all` | Republish global and cached local models |
| `POST` | `/api/mqtt/federated/training` | Send a `START_TRAINING` command |

Examples:

```powershell
curl.exe http://localhost:8080/api/mqtt/federated/status
curl.exe -X POST http://localhost:8080/api/mqtt/federated/model/toggle `
  -H "Content-Type: application/json" `
  -d '{"activeModel":"GLOBAL"}'
curl.exe -X POST http://localhost:8080/api/mqtt/federated/training `
  -H "Content-Type: application/json" `
  -d '{"useQuantization":true}'
```

The training endpoint forwards the command to the Android clients; it does not
train models on the server.

## Configuration

| Variable | Default |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | environment-dependent |
| `APP_MQTT_URL` | `tcp://localhost:1883` |
| `APP_FEDERATED_EXPECTED_CLIENTS` | `2` |
| `APP_MQTT_QOS` | `1` |
| `APP_MQTT_USERNAME` | empty |
| `APP_MQTT_PASSWORD` | empty |
| `APP_MQTT_CONNECT_PUBLISH_ENABLED` | `true` |

Topic settings can also be overridden with `APP_MQTT_DEFAULT_TOPIC`,
`APP_MQTT_INBOUND_TOPIC`, `APP_MQTT_LOCAL_WEIGHTS_TOPIC`,
`APP_MQTT_GLOBAL_WEIGHTS_TOPIC`, `APP_MQTT_COMMANDS_TOPIC`, and
`APP_MQTT_TELEMETRY_TOPIC`.

## WebSocket

Connect dashboard clients to:

```text
ws://localhost:8080/ws/telemetry
```

Each message is the original JSON telemetry payload received from an Android
client.

## Source layout

- `src/main/java/.../config`: Spring Security, MQTT, and WebSocket wiring
- `src/main/java/.../controller`: REST API controllers
- `src/main/java/.../federated`: FedAvg, quantization, and persistence
- `src/main/java/.../mqtt`: MQTT publishing and latest-message storage
- `src/main/java/.../websocket`: WebSocket handlers
- `src/test/java`: controller, application, and codec tests

Each Java source file begins with a short file-level documentation header that
identifies its role; this README is the authoritative operational guide.

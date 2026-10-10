# JAVA_BACKEND

Spring Boot orchestration server for the Android federated pose clients.

## MQTT (local broker)

This API now includes Spring Integration MQTT support configured for a broker on the same machine by default:

- Broker URL: `tcp://localhost:1883`
- Default publish topic: `java_backend/default`
- Subscribed topics: `federated/local/weights` and `federated/telemetry`
- Local weights are decoded using the Android 8-bit format, averaged in groups of
  `APP_FEDERATED_EXPECTED_CLIENTS` (default `2`), and published as binary payloads to
  `federated/global/weights`.
- Configure the broker and federated topics with `APP_MQTT_URL`,
  `APP_MQTT_LOCAL_WEIGHTS_TOPIC`, `APP_MQTT_GLOBAL_WEIGHTS_TOPIC`,
  `APP_MQTT_COMMANDS_TOPIC`, and `APP_FEDERATED_EXPECTED_CLIENTS`.

You can override these with environment variables:

- `APP_MQTT_URL`
- `APP_MQTT_DEFAULT_TOPIC`
- `APP_MQTT_INBOUND_TOPIC`
- `APP_MQTT_QOS`
- `APP_MQTT_USERNAME`
- `APP_MQTT_PASSWORD`

Endpoints:

- `POST /api/mqtt/publish`
  - Body example: `{"payload":"hello mqtt","topic":"java_backend/test","qos":1,"retained":false}`
- `GET /api/mqtt/latest`
  - Returns the latest message received from the subscribed topic filter.
- `POST /api/mqtt/federated/command`
  - Publishes a JSON command, for example
    `{"command":"SET_MODEL","active_model":"GLOBAL"}`, to `federated/commands`.
- `GET /api/mqtt/federated/status`
  - Returns received update count, pending clients, completed rounds, and global model size.
- `POST /api/mqtt/federated/model/toggle`
  - Toggles edge-device inference selection without changing model weights.
  - Body: `{"activeModel":"LOCAL"}` or `{"activeModel":"GLOBAL"}`.

Telemetry published by Android clients on `federated/telemetry` is relayed as
the original JSON payload to dashboard WebSocket clients connected to
`/ws/telemetry`.

### Publish automatically when broker connection comes up

This is enabled by default now. Optional environment variables:

- `APP_MQTT_CONNECT_PUBLISH_ENABLED=true`
- `APP_MQTT_CONNECT_PUBLISH_PAYLOAD=backend online`
- Optional: `APP_MQTT_CONNECT_PUBLISH_TOPIC=java_backend/status` (defaults to `app.mqtt.default-topic`)
- Optional: `APP_MQTT_CONNECT_PUBLISH_QOS=1`
- Optional: `APP_MQTT_CONNECT_PUBLISH_RETAINED=true`

When enabled, the app publishes this message after MQTT subscription connection is established (including reconnects).

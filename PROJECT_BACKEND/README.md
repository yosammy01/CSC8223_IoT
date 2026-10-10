# JAVA_BACKEND
JAVA_BACKEND

## MQTT (local broker)

This API now includes Spring Integration MQTT support configured for a broker on the same machine by default:

- Broker URL: `tcp://localhost:1883`
- Default publish topic: `java_backend/default`
- Subscribed topic filter: `java_backend/#`

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

### Publish automatically when broker connection comes up

This is enabled by default now. Optional environment variables:

- `APP_MQTT_CONNECT_PUBLISH_ENABLED=true`
- `APP_MQTT_CONNECT_PUBLISH_PAYLOAD=backend online`
- Optional: `APP_MQTT_CONNECT_PUBLISH_TOPIC=java_backend/status` (defaults to `app.mqtt.default-topic`)
- Optional: `APP_MQTT_CONNECT_PUBLISH_QOS=1`
- Optional: `APP_MQTT_CONNECT_PUBLISH_RETAINED=true`

When enabled, the app publishes this message after MQTT subscription connection is established (including reconnects).

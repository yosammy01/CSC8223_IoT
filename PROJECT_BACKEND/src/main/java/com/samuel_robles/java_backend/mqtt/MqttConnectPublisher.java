/**
 * Optionally publishes a status message after the MQTT subscription connects.
 * See README.md for connection-publish configuration.
 */
package com.samuel_robles.java_backend.mqtt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
/** Defines the MqttConnectPublisher component. */
public class MqttConnectPublisher {

    /** Stores the mqttPublisher value. */
    private final MqttPublisher mqttPublisher;
    /** Stores the enabled value. */
    private final boolean enabled;
    /** Stores the topic value. */
    private final String topic;
    /** Stores the payload value. */
    private final String payload;
    /** Stores the qos value. */
    private final int qos;
    /** Stores the retained value. */
    private final boolean retained;

    /** Performs the MqttConnectPublisher operation. */
    public MqttConnectPublisher(
            MqttPublisher mqttPublisher,
            @Value("${app.mqtt.connect-publish.enabled:false}") boolean enabled,
            @Value("${app.mqtt.connect-publish.topic:}") String topic,
            @Value("${app.mqtt.connect-publish.payload:}") String payload,
            @Value("${app.mqtt.connect-publish.qos}") int qos,
            @Value("${app.mqtt.connect-publish.retained:false}") boolean retained) {
        this.mqttPublisher = mqttPublisher;
        this.enabled = enabled;
        this.topic = topic;
        this.payload = payload;
        this.qos = qos;
        this.retained = retained;
    }

    @EventListener(MqttSubscribedEvent.class)
    /** Performs the publishOnConnect operation. */
    public void publishOnConnect() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(payload)) {
            throw new IllegalStateException("app.mqtt.connect-publish.payload must not be blank when enabled.");
        }

        String targetTopic = StringUtils.hasText(topic) ? topic : null;
        mqttPublisher.publish(payload, targetTopic, qos, retained);
    }
}

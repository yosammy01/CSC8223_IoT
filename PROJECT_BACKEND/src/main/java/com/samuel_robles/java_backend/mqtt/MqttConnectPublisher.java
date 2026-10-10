package com.samuel_robles.java_backend.mqtt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MqttConnectPublisher {

    private final MqttPublisher mqttPublisher;
    private final boolean enabled;
    private final String topic;
    private final String payload;
    private final int qos;
    private final boolean retained;

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

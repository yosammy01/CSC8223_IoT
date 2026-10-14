package com.samuel_robles.java_backend.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

import com.samuel_robles.java_backend.mqtt.MqttMessageStore;
import com.samuel_robles.java_backend.federated.FederatedAggregationService;
import com.samuel_robles.java_backend.websocket.TelemetryWebSocketHandler;

@Configuration
public class MqttIntegrationConfig {

    @Bean
    MqttPahoClientFactory mqttClientFactory(
            @Value("${app.mqtt.url}") String brokerUrl,
            @Value("${app.mqtt.username:}") String username,
            @Value("${app.mqtt.password:}") String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { brokerUrl });
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (StringUtils.hasText(username)) {
            options.setUserName(username);
        }
        if (StringUtils.hasText(password)) {
            options.setPassword(password.toCharArray());
        }

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    MessageHandler mqttOutboundHandler(
            MqttPahoClientFactory mqttClientFactory,
            @Value("${app.mqtt.client-id-prefix}") String clientIdPrefix,
            @Value("${app.mqtt.default-topic}") String defaultTopic,
            @Value("${app.mqtt.qos}") int qos) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientIdPrefix + "-publisher", mqttClientFactory);
        handler.setAsync(true);
        handler.setDefaultQos(qos);
        handler.setDefaultRetained(false);
        handler.setDefaultTopic(defaultTopic);
        return handler;
    }

    @Bean
    MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    MessageProducer mqttInboundAdapter(
            MqttPahoClientFactory mqttClientFactory,
            @Value("${app.mqtt.client-id-prefix}") String clientIdPrefix,
            @Value("${app.mqtt.inbound-topic}") String inboundTopic,
            @Value("${app.mqtt.local-weights-topic:federated/local/weights}") String localWeightsTopic,
            @Value("${app.mqtt.qos}") int qos) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientIdPrefix + "-subscriber",
                mqttClientFactory,
        topicFilter(inboundTopic), topicFilter(localWeightsTopic));
        adapter.setQos(qos);
        adapter.setCompletionTimeout(5000);
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    MessageHandler mqttInboundHandler(
            MqttMessageStore mqttMessageStore,
            FederatedAggregationService aggregationService,
            TelemetryWebSocketHandler telemetryWebSocketHandler,
            @Value("${app.mqtt.local-weights-topic:federated/local/weights}") String localWeightsTopic,
            @Value("${app.mqtt.telemetry-topic:federated/telemetry}") String telemetryTopic) {
        return message -> {
            mqttMessageStore.updateFromMessage(message);
            String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
            if (topicMatches(localWeightsTopic, topic)) {
                Object payload = message.getPayload();
                if (!(payload instanceof byte[] bytes)) {
                    throw new IllegalArgumentException("Local federated weights must be an MQTT binary payload.");
                }
                aggregationService.accept(bytes);
            } else if (topicMatches(telemetryTopic, topic)) {
                Object payload = message.getPayload();
                String telemetry = payload instanceof byte[] bytes
                        ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                        : String.valueOf(payload);
                telemetryWebSocketHandler.broadcast(telemetry);
            }
        };
    }

    private static boolean topicMatches(String configuredTopic, String receivedTopic) {
        if (configuredTopic == null || receivedTopic == null) {
            return false;
        }
        String baseTopic = configuredTopic.endsWith("/#")
                ? configuredTopic.substring(0, configuredTopic.length() - 2)
                : configuredTopic;
        return receivedTopic.equals(baseTopic) || receivedTopic.startsWith(baseTopic + "/");
    }

    private static String topicFilter(String configuredTopic) {
        return configuredTopic.endsWith("/#") ? configuredTopic : configuredTopic + "/#";
    }
}

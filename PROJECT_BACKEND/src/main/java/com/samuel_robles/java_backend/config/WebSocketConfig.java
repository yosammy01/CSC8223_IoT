package com.samuel_robles.java_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.samuel_robles.java_backend.websocket.GenericWebSocketHandler;
import com.samuel_robles.java_backend.websocket.SecondsWebSocketHandler;
import com.samuel_robles.java_backend.websocket.TelemetryWebSocketHandler;
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GenericWebSocketHandler websocketHandler;
    private final SecondsWebSocketHandler secondsWebSocketHandler;
private final TelemetryWebSocketHandler telemetryWebSocketHandler;

public WebSocketConfig(
        GenericWebSocketHandler websocketHandler,
        SecondsWebSocketHandler secondsWebSocketHandler,
        TelemetryWebSocketHandler telemetryWebSocketHandler) {
    this.websocketHandler = websocketHandler;
    this.secondsWebSocketHandler = secondsWebSocketHandler;
    this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(secondsWebSocketHandler, "/ws/seconds").setAllowedOrigins("*");
        registry.addHandler(websocketHandler, "/ws/generic").setAllowedOrigins("*");
        registry.addHandler(telemetryWebSocketHandler, "/ws/telemetry").setAllowedOrigins("*");
    }
}

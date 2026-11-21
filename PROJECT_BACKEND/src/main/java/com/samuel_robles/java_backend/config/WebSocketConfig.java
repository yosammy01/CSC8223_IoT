/**
 * Registers the backend WebSocket endpoints for dashboard and device streams.
 * See README.md for the telemetry connection URL.
 */
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
/** Defines the WebSocketConfig component. */
public class WebSocketConfig implements WebSocketConfigurer {

    /** Stores the websocketHandler value. */
    private final GenericWebSocketHandler websocketHandler;
    /** Stores the secondsWebSocketHandler value. */
    private final SecondsWebSocketHandler secondsWebSocketHandler;
/** Stores the telemetryWebSocketHandler value. */
private final TelemetryWebSocketHandler telemetryWebSocketHandler;

/** Performs the WebSocketConfig operation. */
public WebSocketConfig(
        GenericWebSocketHandler websocketHandler,
        SecondsWebSocketHandler secondsWebSocketHandler,
        TelemetryWebSocketHandler telemetryWebSocketHandler) {
    this.websocketHandler = websocketHandler;
    this.secondsWebSocketHandler = secondsWebSocketHandler;
    this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }
    @Override
    /** Performs the registerWebSocketHandlers operation. */
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Demonstration-only endpoints; not used by the Android or React data flow.
        registry.addHandler(secondsWebSocketHandler, "/ws/seconds").setAllowedOrigins("*");
        registry.addHandler(websocketHandler, "/ws/generic").setAllowedOrigins("*");
        registry.addHandler(telemetryWebSocketHandler, "/ws/telemetry").setAllowedOrigins("*");
    }
}

package com.samuel_robles.java_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.samuel_robles.java_backend.websocket.GenericWebSocketHandler;
import com.samuel_robles.java_backend.websocket.SecondsWebSocketHandler;
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GenericWebSocketHandler websocketHandler;
    private final SecondsWebSocketHandler secondsWebSocketHandler;
    public WebSocketConfig(GenericWebSocketHandler websocketHandler, SecondsWebSocketHandler secondsWebSocketHandler) {
        this.websocketHandler = websocketHandler;
        this.secondsWebSocketHandler = secondsWebSocketHandler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(secondsWebSocketHandler, "/ws/seconds").setAllowedOrigins("*");
        registry.addHandler(websocketHandler, "/ws/generic").setAllowedOrigins("*");
    }
}

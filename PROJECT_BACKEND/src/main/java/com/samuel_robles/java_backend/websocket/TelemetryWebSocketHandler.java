/**
 * Broadcasts MQTT telemetry to connected dashboard WebSocket clients.
 * See README.md for the telemetry topic and connection URL.
 */
package com.samuel_robles.java_backend.websocket;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;

@Component
/** Defines the TelemetryWebSocketHandler component. */
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    /** Stores connected telemetry sessions by identifier. */
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    /** Performs the afterConnectionEstablished operation. */
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    /** Performs the broadcast operation. */
    public void broadcast(String telemetryJson) {
        TextMessage message = new TextMessage(telemetryJson);
        sessions.values().removeIf(session -> {
            if (!session.isOpen()) {
                return true;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
                return false;
            } catch (IOException exception) {
                return true;
            }
        });
    }

    @Override
    /** Performs the afterConnectionClosed operation. */
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    /** Performs the handleTransportError operation. */
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @PreDestroy
    /** Performs the shutdown operation. */
    public void shutdown() {
        sessions.clear();
    }
}

/**
 * Handles the generic WebSocket endpoint reserved for dashboard extensions.
 * See README.md for the backend WebSocket surface.
 */
package com.samuel_robles.java_backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class GenericWebSocketHandler extends TextWebSocketHandler {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentMap<WebSocketSession, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<WebSocketSession, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        counters.put(session, counter);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            if (session.isOpen()) {
                try {
                    int sec = counter.incrementAndGet();
                    session.sendMessage(new TextMessage(String.valueOf(sec)));
                } catch (Exception ignored) {
                    // ignore send failures for this simple example
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        tasks.put(session, future);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ScheduledFuture<?> f = tasks.remove(session);
        if (f != null) f.cancel(true);
        counters.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        ScheduledFuture<?> f = tasks.remove(session);
        if (f != null) f.cancel(true);
        counters.remove(session);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

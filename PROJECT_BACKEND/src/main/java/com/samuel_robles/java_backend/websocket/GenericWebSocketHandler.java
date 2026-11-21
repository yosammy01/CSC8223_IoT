/**
 * Demonstration-only WebSocket handler for the generic endpoint.
 * This endpoint is not used by the Android or dashboard federated-learning flows.
 * It is registered in WebSocketConfig for compatibility and manual testing.
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
/** Defines the GenericWebSocketHandler component. */
public class GenericWebSocketHandler extends TextWebSocketHandler {

    /** Performs the newScheduledThreadPool operation. */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    /** Stores the tasks value. */
    private final ConcurrentMap<WebSocketSession, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    /** Stores per-session elapsed-time counters. */
    private final ConcurrentMap<WebSocketSession, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    /** Performs the afterConnectionEstablished operation. */
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
    /** Performs the afterConnectionClosed operation. */
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ScheduledFuture<?> f = tasks.remove(session);
        if (f != null) f.cancel(true);
        counters.remove(session);
    }

    @Override
    /** Performs the handleTransportError operation. */
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        ScheduledFuture<?> f = tasks.remove(session);
        if (f != null) f.cancel(true);
        counters.remove(session);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    @PreDestroy
    /** Performs the shutdown operation. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

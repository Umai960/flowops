package com.company.kanban.service;

import com.company.kanban.dto.ChatDtos.ChatEvent;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.User;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one live connection (Server-Sent Events) per open FlowOps tab and pushes chat
 * changes to the people allowed to read that chat, so messages appear without refreshing.
 */
@Component
public class ChatEventBroker {
    private static final long CONNECTION_TIMEOUT_MS = 15 * 60 * 1000L;

    /** Published by ChatService; delivered only after the database transaction commits. */
    public record Delivery(ChatEvent event, Long departmentId, Set<Long> participantIds) {}

    private record Listener(Long userId, boolean admin, Long departmentId) {}

    private final Map<SseEmitter, Listener> listeners = new ConcurrentHashMap<>();

    public SseEmitter subscribe(User user) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        listeners.put(emitter, new Listener(user.getId(), user.getRole() == Role.ADMIN,
                user.getDepartment() == null ? null : user.getDepartment().getId()));
        emitter.onCompletion(() -> listeners.remove(emitter));
        emitter.onTimeout(() -> { listeners.remove(emitter); emitter.complete(); });
        emitter.onError(error -> listeners.remove(emitter));
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void deliver(Delivery delivery) {
        listeners.forEach((emitter, listener) -> {
            if (canReceive(listener, delivery)) send(emitter, SseEmitter.event().name("chat").data(delivery.event(), MediaType.APPLICATION_JSON));
        });
    }

    // Keeps idle connections open through proxies and lets the server notice closed tabs.
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        listeners.keySet().forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    // Close open tabs' connections first so a service stop or restart does not wait for them
    // (graceful shutdown would otherwise wait for every open live stream). Browsers reconnect by themselves.
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void closeAll() {
        listeners.keySet().forEach(emitter -> { try { emitter.complete(); } catch (Exception ignored) { /* already closed */ } });
        listeners.clear();
    }

    private static boolean canReceive(Listener listener, Delivery delivery) {
        return listener.admin()
                || delivery.participantIds().contains(listener.userId())
                || (delivery.departmentId() != null && Objects.equals(delivery.departmentId(), listener.departmentId()));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception closed) {
            listeners.remove(emitter);
            try { emitter.complete(); } catch (Exception ignored) { /* connection already gone */ }
        }
    }
}

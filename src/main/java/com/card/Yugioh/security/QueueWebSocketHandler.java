package com.card.Yugioh.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.card.Yugioh.service.QueueNotifier;

import lombok.extern.slf4j.Slf4j;

@Component("queueWebSocketHandlerSecure")
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler implements QueueNotifier {

    private final ApplicationEventPublisher publisher;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public QueueWebSocketHandler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        QueueIdentity identity = identityOf(session);
        if (identity == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ex) {
                log.debug("Invalid queue socket could not be closed", ex);
            }
            return;
        }
        String group = identity.group();
        String qid = identity.qid();
        String userId = identity.userId();

        String key = userKey(group, userId);
        WebSocketSession old = sessions.put(key, session);
        if (old != null && old.isOpen()) {
            try {
                old.close(CloseStatus.NORMAL);
            } catch (Exception ignore) {
            }
        }
        log.debug("WS OPEN group={} qid={} userId={}", group, qid, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        QueueIdentity identity = identityOf(session);
        if (identity == null) return;
        String group = identity.group();
        String qid = identity.qid();
        String userId = identity.userId();
        boolean removedCurrentSession = sessions.remove(userKey(group, userId), session);

        log.debug(
            "WS CLOSE group={} qid={} userId={} activeSessionRemoved={}",
            group,
            qid,
            userId,
            removedCurrentSession
        );

        if (removedCurrentSession) {
            publisher.publishEvent(new UserDisconnectedEvent(group, qid, userId));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!"PING".equalsIgnoreCase(message.getPayload())) {
            return;
        }
        QueueIdentity identity = identityOf(session);
        if (identity == null || sessions.get(userKey(identity.group(), identity.userId())) != session) return;
        publisher.publishEvent(new UserPingEvent(identity.group(), identity.qid(), identity.userId()));
    }

    @Override
    public void broadcast(String msg) {
        sessions.values().forEach(session -> sendSilently(session, msg));
    }

    @Override
    public void sendToUser(String group, String userId, String msg) {
        WebSocketSession session = sessions.get(userKey(group, userId));
        if (session != null) {
            sendSilently(session, msg);
        }
    }

    private void sendSilently(WebSocketSession session, String message) {
        try {
            // Scheduler and request threads may notify the same socket concurrently.
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(message));
            }
        } catch (Exception ex) {
            log.warn("WS send fail {}", session.getId(), ex);
        }
    }

    private static String userKey(String group, String userId) {
        return group + "|" + userId;
    }

    private static QueueIdentity identityOf(WebSocketSession session) {
        if (session.getUri() == null) return null;
        var query = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String group = query.getFirst("group");
        String qid = query.getFirst("qid");
        String userId = query.getFirst("userId");
        if (!("site".equals(group) || "predict".equals(group))
                || !("main".equals(qid) || "vip".equals(qid))
                || userId == null || userId.isBlank()) return null;
        return new QueueIdentity(group, qid, userId);
    }

    private record QueueIdentity(String group, String qid, String userId) {}
}

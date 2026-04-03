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
        if (session.getUri() == null) {
            return;
        }

        var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String group = qs.getFirst("group");
        String qid = qs.getFirst("qid");
        String userId = qs.getFirst("userId");

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
        String group = null;
        String qid = null;
        String userId = null;
        boolean removedCurrentSession = false;

        if (session.getUri() != null) {
            var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
            group = qs.getFirst("group");
            qid = qs.getFirst("qid");
            userId = qs.getFirst("userId");
            removedCurrentSession = sessions.remove(userKey(group, userId), session);
        } else {
            removedCurrentSession = sessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
        }

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
        if (!"PING".equalsIgnoreCase(message.getPayload()) || session.getUri() == null) {
            return;
        }

        var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String group = qs.getFirst("group");
        String userId = qs.getFirst("userId");
        String qid = qs.getFirst("qid");
        publisher.publishEvent(new UserPingEvent(group, qid, userId));
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
            session.sendMessage(new TextMessage(message));
        } catch (Exception ex) {
            log.warn("WS send fail {}", session.getId(), ex);
        }
    }

    private static String userKey(String group, String userId) {
        return group + "|" + userId;
    }
}

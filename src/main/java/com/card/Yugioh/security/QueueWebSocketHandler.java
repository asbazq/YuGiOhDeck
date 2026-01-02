package com.card.Yugioh.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.card.Yugioh.service.QueueNotifier;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component("queueWebSocketHandlerSecure")
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler
                                   implements QueueNotifier {

    private final ApplicationEventPublisher publisher;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public QueueWebSocketHandler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
    if (session.getUri() == null) return;
    var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
    String group  = qs.getFirst("group");
    String qid    = qs.getFirst("qid");
    String userId = qs.getFirst("userId");

    String k = userKey(group, userId);
    WebSocketSession old = sessions.put(k, session);
    if (old != null && old.isOpen()) {
        try { old.close(CloseStatus.NORMAL); } catch (Exception ignore) {}
    }
    log.debug("WS OPEN group={} qid={} userId={}", group, qid, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String group = null, qid = null, userId = null;
    if (session.getUri() != null) {
        var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        group  = qs.getFirst("group");
        qid    = qs.getFirst("qid");
        userId = qs.getFirst("userId");
        sessions.remove(userKey(group, userId));   // ★ 키로 제거
    } else {
        sessions.values().removeIf(s -> s.equals(session));
    }
    log.debug("WS CLOSE group={} qid={} userId={}", group, qid, userId);
    publisher.publishEvent(new UserDisconnectedEvent(group, qid, userId));
    }


    /* 수신 메시지 처리 (PING → TTL 갱신) */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("PING".equalsIgnoreCase(message.getPayload())) {
            String group = UriComponentsBuilder.fromUri(session.getUri())
                                            .build().getQueryParams().getFirst("group");
            String userId = UriComponentsBuilder.fromUri(session.getUri())
                    .build().getQueryParams().getFirst("userId");
            String qid = UriComponentsBuilder.fromUri(session.getUri())
                    .build().getQueryParams().getFirst("qid");
            publisher.publishEvent(new UserPingEvent(group, qid, userId));
        }
    }

    /* ============== QueueNotifier 구현 ============== */

    @Override
    public void broadcast(String msg) {
        sessions.values().forEach(s -> sendSilently(s, msg));
    }

    @Override
    public void sendToUser(String group, String userId, String msg) {
        WebSocketSession s = sessions.get(userKey(group, userId));
        if (s != null) sendSilently(s, msg);
    }

    private void sendSilently(WebSocketSession s, String m) {
        try { 
            s.sendMessage(new TextMessage(m)); 
        } catch (Exception ex) { 
            log.warn("WS send fail {}", s.getId(), ex); 
        }
    }

    private static String userKey(String group, String userId) {
        return group + "|" + userId;
    }
}

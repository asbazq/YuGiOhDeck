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

@Component
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler
                                   implements QueueNotifier {

    private final ApplicationEventPublisher publisher;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public QueueWebSocketHandler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /* 연결 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = UriComponentsBuilder.fromUri(session.getUri())
                                            .build().getQueryParams()
                                            .getFirst("userId");

        String qid = UriComponentsBuilder.fromUri(session.getUri())
                                            .build().getQueryParams()
                                            .getFirst("qid");

        sessions.put(userId, session);
        log.debug("WS OPEN qid={} userId={}", qid, userId);
    }

    /* 종료 → 이벤트 발행 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.entrySet().removeIf(e -> e.getValue().equals(session));
        String userId = session.getUri() != null
                ? UriComponentsBuilder.fromUri(session.getUri()).build()
                        .getQueryParams().getFirst("userId")
                : "unknown";

        String qid = session.getUri() != null
                ? UriComponentsBuilder.fromUri(session.getUri()).build()
                        .getQueryParams().getFirst("qid")
                : "unknown";
        log.debug("WS CLOSE qid={} userId={}", qid, userId);
        publisher.publishEvent(new UserDisconnectedEvent(qid, userId));
    }

    /* ============== QueueNotifier 구현 ============== */

    @Override
    public void broadcast(String msg) {
        sessions.values().forEach(s -> sendSilently(s, msg));
    }

    @Override
    public void sendToUser(String userId, String msg) {
        WebSocketSession s = sessions.get(userId);
        if (s != null) sendSilently(s, msg);
    }

    private void sendSilently(WebSocketSession s, String m) {
        try { 
            s.sendMessage(new TextMessage(m)); 
        } catch (Exception ex) { 
            log.warn("WS send fail {}", s.getId(), ex); 
        }
    }
}

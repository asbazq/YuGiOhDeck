package com.card.Yugioh.webSocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler {
    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private static final Map<String, Boolean> userRefreshStatus = new ConcurrentHashMap<>(); // userId -> refresh 상태 매핑

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("새로운 WebSocket 연결: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        String userId = getUserIdBySession(session);
        if (userId != null) {
            userRefreshStatus.remove(userId);
            log.info("WebSocket 연결 종료: {}, User ID: {}", session.getId(), userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("수신한 메시지: {}", payload);

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> messageData = objectMapper.readValue(payload, new TypeReference<>() {});

        String action = (String) messageData.get("action");
        String userId = (String) messageData.get("userId");

        if ("refreshStatus".equals(action)) {
            boolean isRefresh = Boolean.parseBoolean(messageData.get("isRefresh").toString());
            userRefreshStatus.put(userId, isRefresh);
            log.info("사용자 {}로부터 새로고침 상태 수신: {}", userId, isRefresh);
        }
    }

    public Boolean consumeRefreshStatus(String userId) {
        Boolean isRefresh = userRefreshStatus.get(userId);
        if (isRefresh != null) {
            userRefreshStatus.put(userId, false); // 값을 읽은 뒤 false로 초기화
            log.info("사용자 {} 새로고침 상태 소비 후 초기화", userId);
        }
        return isRefresh != null && isRefresh;
    }
    
    public void broadcastMessage(String message) {
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.error("메시지 전송 실패: {}", session.getId(), e);
            }
        }
    }

    private String getUserIdBySession(WebSocketSession session) {
        return userRefreshStatus.entrySet()
                .stream()
                .filter(entry -> session.getId().equals(entry.getKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}

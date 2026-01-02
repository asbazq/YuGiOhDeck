package com.card.Yugioh.webSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.card.Yugioh.service.WebAPIService;

@Component("queueWebSocketHandlerPublic")
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler {

    private final WebAPIService webAPIService;
    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public QueueWebSocketHandler(WebAPIService webAPIService) {
        this.webAPIService = webAPIService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("새로운 WebSocket 연결: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket 연결 종료: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            String action = (String) data.get("action");
            String userId = (String) data.get("userId");

            if ("joinQueue".equals(action)) {
                session.getAttributes().put("userId", userId);
                Long position = webAPIService.getUserPosition(userId);
                String responseMessage = String.format("{\"action\":\"position\", \"userId\":\"%s\", \"position\":%d}", userId, position);
                session.sendMessage(new TextMessage(responseMessage));
            }
        } catch (Exception e) {
            log.error("WebSocket 메시지 처리 중 오류 발생: {}", e.getMessage());
            try {
                session.sendMessage(new TextMessage("{\"action\":\"error\", \"message\":\"Invalid request\"}"));
            } catch (Exception sendError) {
                log.error("에러 메시지 전송 실패: {}", sendError.getMessage());
            }
        }
    }

    public void sendMessageToUser(String userId, String message) {
        sessions.stream()
                .filter(session -> session.isOpen() && userId.equals(session.getAttributes().get("userId")))
                .forEach(session -> {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (Exception e) {
                        log.error("메시지 전송 실패: {}", session.getId(), e);
                    }
                });
    }

    public void broadcastQueueStatus(long waitingCount, long runningCount, long finishedCount) {
        String message = String.format("{\"waiting\": %d, \"running\": %d, \"finished\": %d}", waitingCount, runningCount, finishedCount);
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.error("WebSocket 메시지 전송 실패: {}", session.getId(), e);
            }
        }
        log.info("대기열 상태 브로드캐스트 완료: {}", message);
    }

    public void broadcastMessage(String message) {
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                log.error("WebSocket 메시지 브로드캐스트 실패: {}", e.getMessage());
            }
        }
        log.info("메시지 브로드캐스트 완료: {}", message);
    }
    
}

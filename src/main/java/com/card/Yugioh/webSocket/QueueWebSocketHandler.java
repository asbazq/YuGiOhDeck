package com.card.Yugioh.webSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.card.Yugioh.service.WebAPIService;

@Component("queueWebSocketHandlerPublic")
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler {

    private final WebAPIService webAPIService;
    private final ObjectMapper objectMapper;
    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public QueueWebSocketHandler(WebAPIService webAPIService, ObjectMapper objectMapper) {
        this.webAPIService = webAPIService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("새로운 WebSocket 연결: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("WebSocket 연결 종료: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            String action = (String) data.get("action");
            String userId = (String) data.get("userId");

            if ("joinQueue".equals(action)) {
                if (userId == null || userId.isBlank()) {
                    session.sendMessage(new TextMessage(
                        objectMapper.createObjectNode()
                            .put("action", "error")
                            .put("message", "userId is required")
                            .toString()
                    ));
                    return;
                }
                session.getAttributes().put("userId", userId);
                sendCurrentState(session, userId);
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
        String message = objectMapper.createObjectNode()
                .put("action", "queueStatus")
                .put("waiting", waitingCount)
                .put("running", runningCount)
                .put("finished", finishedCount)
                .toString();
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                log.error("WebSocket 메시지 전송 실패: {}", session.getId(), e);
            }
        }
        log.debug("대기열 상태 브로드캐스트 완료: {}", message);
    }

    private void sendCurrentState(WebSocketSession session, String userId) throws Exception {
        ObjectNode response = objectMapper.createObjectNode().put("userId", userId);
        Long position = webAPIService.getUserPosition(userId);

        if (position != null) {
            response.put("action", "position").put("position", position);
        } else if (webAPIService.isInRunning(userId)) {
            response.put("action", "redirect").put("url", "/index.html");
        } else {
            response.put("action", "queueEmpty");
        }

        if (session.isOpen()) {
            session.sendMessage(new TextMessage(response.toString()));
        }
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
        log.debug("메시지 브로드캐스트 완료: {}", message);
    }
    
}

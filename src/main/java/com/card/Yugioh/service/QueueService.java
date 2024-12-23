package com.card.Yugioh.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.card.Yugioh.webSocket.QueueWebSocketHandler;

@Service
public class QueueService {
    private static final String QUEUE_KEY = "userQueue";
    private static final int MAX_USERS = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final QueueWebSocketHandler webSocketHandler;

    public QueueService(RedisTemplate<String, Object> redisTemplate, QueueWebSocketHandler webSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    public void addUserToQueue(String userId) {
        long userCount = redisTemplate.opsForList().size(QUEUE_KEY);
        if (userCount < MAX_USERS) {
            // 즉시 사이트 접근 가능
            grantAccess(userId);
        } else {
            // 대기열에 추가
            redisTemplate.opsForList().leftPush(QUEUE_KEY, userId);
            webSocketHandler.broadcastMessage("User " + userId + " added to queue. Position: " + (userCount - MAX_USERS + 1));
        }
    }

    public void removeUserFromQueue(String userId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 1, userId);
        // 대기열에서 사용자 이동
        String nextUser = (String) redisTemplate.opsForList().rightPop(QUEUE_KEY);
        if (nextUser != null) {
            grantAccess(nextUser);
            webSocketHandler.broadcastMessage("User " + nextUser + " granted access to the site.");
        }
    }

    private void grantAccess(String userId) {
        // 사용자에게 사이트 접근 권한 부여하는 로직 구현
        System.out.println("Access granted to user: " + userId);
        webSocketHandler.broadcastMessage("User " + userId + " has entered the site.");
    }
}
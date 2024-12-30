package com.card.Yugioh.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.card.Yugioh.webSocket.QueueWebSocketHandler;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class QueueService {
    private static final String QUEUE_KEY = "userQueue";
    private static final String CONNECTED_USERS_KEY = "connectedUsers";
    public static final int MAX_USERS = 3;

    private final RedisTemplate<String, Object> redisTemplate;
    private final QueueWebSocketHandler webSocketHandler;

    public QueueService(RedisTemplate<String, Object> redisTemplate, QueueWebSocketHandler webSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    public String handleUserEntry(String userId) {
        String lockKey = "user_entry_lock:" + userId;
        String lockValue = UUID.randomUUID().toString(); // 고유 소유권 값

        // Redis 락 생성
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));
        if (acquired == null || !acquired) {
            log.warn("User {} is already being processed.", userId);
            return "/queue.html"; // 이미 처리 중인 경우 대기열로 이동
        }

        try {
            // 중복 확인 및 처리
            if (redisTemplate.opsForSet().isMember(CONNECTED_USERS_KEY, userId)) {
                log.warn("User {} is already connected.", userId);
                return "/index.html";
            }

            List<Object> queue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
            if (queue.contains(userId)) {
                log.warn("User {} is already in the queue.", userId);
                return "/queue.html";
            }

            long connectedUsers = getConnectedUsersCount();
            log.info("현재 접속 인원: {}", connectedUsers + 1);

            if (connectedUsers < MAX_USERS) {
                addConnectedUser(userId);
                grantAccess(userId);
                return "/index.html";
            } else {
                addUserToQueue(userId);
                return "/queue.html";
            }
        } finally {
            // 락 소유권 확인 후 해제
            Object currentValue = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                redisTemplate.delete(lockKey);
            } else {
                log.warn("Failed to release lock for userId: {}. Lock value mismatch.", userId);
            }
        }
    }

    
    public void addConnectedUser(String userId) {
        if (!redisTemplate.opsForSet().isMember(CONNECTED_USERS_KEY, userId)) {
            redisTemplate.opsForSet().add(CONNECTED_USERS_KEY, userId);
            broadcastQueueStatus();
            log.info("Added user {} to connected users.", userId);
        } else {
            log.warn("User {} is already in connected users.", userId);
        }
    }
    

    public void removeConnectedUser(String userId) {
        redisTemplate.opsForSet().remove(CONNECTED_USERS_KEY, userId);
        broadcastQueueStatus();
        log.info("사용자 제거 결과: " + userId + " - 현재 접속 인원: " + getConnectedUsersCount());
    }

    public long getConnectedUsersCount() {
        return redisTemplate.opsForSet().size(CONNECTED_USERS_KEY);
    }

    public long getQueueUserCount() {
        return redisTemplate.opsForList().size(QUEUE_KEY);
    }

    public void addUserToQueue(String userId) {
        List<Object> queue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        if (!queue.contains(userId)) {
            redisTemplate.opsForList().rightPush(QUEUE_KEY, userId);
            log.info("Added user {} to queue.", userId);
            broadcastQueueStatus(); // 대기열 상태 갱신
            log.info("현재 대기열 인원 {}", getQueueUserCount());
        } else {
            log.warn("User {} is already in the queue.", userId);
        }
    }
    

    public void removeUserFromQueue(String userId) {
        // 대기열에서 사용자 제거
        Long result = redisTemplate.opsForList().remove(QUEUE_KEY, 1, userId);
        if (result != null && result > 0) {
            log.info("Removed user {} from queue.", userId);
            broadcastQueueStatus(); // 대기열 상태 변경 시 브로드캐스트
            log.info("현재 대기열 인원 {}", getQueueUserCount());
        } else {
            log.warn("User {} was not in the queue.", userId);
        }

        // 대기열의 다음 사용자에게 권한 부여
        String nextUser = (String) redisTemplate.opsForList().rightPop(QUEUE_KEY);
        if (nextUser != null && getConnectedUsersCount() < MAX_USERS) {
            grantAccess(nextUser);
            addConnectedUser(nextUser); // 새 사용자 접속 처리
        }
    }

    private void grantAccess(String userId) {
        log.info("Access granted to user: " + userId);
        webSocketHandler.broadcastMessage("{\"action\": \"redirect\", \"userId\": \"" + userId + "\", \"url\": \"/index.html\"}");
        broadcastQueueStatus();
    }

    public List<Object> getQueueStatus() {
        return redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
    }

    public long getQueuePosition(String userId) {
        List<Object> queue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        return queue.indexOf(userId) + 1; // 1-based index
    }

    public void moveToQueue(String userId) {
        if (redisTemplate.opsForSet().isMember(CONNECTED_USERS_KEY, userId)) {
            redisTemplate.opsForSet().remove(CONNECTED_USERS_KEY, userId);
            addUserToQueue(userId);
            log.info("Moved connected user {} to queue.", userId);
        }
    }

    public void refreshQueuePosition(String userId) {
        if (redisTemplate.opsForList().remove(QUEUE_KEY, 1, userId) > 0) {
            redisTemplate.opsForList().rightPush(QUEUE_KEY, userId);
            log.info("Refreshed position for user {} in queue.", userId);
            broadcastQueueStatus(); // 대기열 상태 갱신
        }
    }

    public boolean isConnectedUser(String userId) {
        return redisTemplate.opsForSet().isMember(CONNECTED_USERS_KEY, userId);
    }
    

    // @Scheduled(fixedRate = 10000) // 10초마다 실행
    public void broadcastQueueStatus() {
        List<Object> queue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        for (int i = 0; i < queue.size(); i++) {
            String userId = (String) queue.get(i);
            long position = i + 1;
            webSocketHandler.broadcastMessage("{\"action\": \"position\", \"userId\": \"" + userId + "\", \"position\": " + position + "}");
        }
    }

    @Scheduled(fixedRate = 60000) // 1분마다 실행 (백업용)
    public void scheduledBroadcastQueueStatus() {
        log.info("Scheduled queue status broadcast initiated.");
        broadcastQueueStatus();
    }

}

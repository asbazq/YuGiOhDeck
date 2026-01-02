package com.card.Yugioh.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import com.card.Yugioh.webSocket.QueueWebSocketHandler;

import java.time.Instant;
import java.util.Set;

@Service
@Profile("!crawler")
public class JobService {

    private static final String WAITING_KEY = "queue:waiting";
    private static final String RUNNING_KEY = "queue:running";
    private static final String FINISHED_KEY = "queue:finished";

    private final RedisTemplate<String, Object> redisTemplate;
    private final QueueWebSocketHandler webSocketHandler;
    public JobService(RedisTemplate<String, Object> redisTemplate, QueueWebSocketHandler webSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 주기적으로 대기열에서 사용자를 실행 상태로 이동시키는 작업 수행.
     */
    // @Scheduled(fixedRate = 5000) // 5초마다 실행
    public void processQueue(long threshold) {
        long currentTime = Instant.now().toEpochMilli();
        Set<Object> users = redisTemplate.opsForZSet().range(WAITING_KEY, 0, threshold - 1);
    
        if (users != null) {
            for (Object user : users) {
                redisTemplate.opsForZSet().add(RUNNING_KEY, user, currentTime);
                redisTemplate.opsForZSet().remove(WAITING_KEY, user);
    
                String message = String.format("{\"action\":\"redirect\", \"userId\":\"%s\", \"url\":\"/index.html\"}", user);
                webSocketHandler.broadcastMessage(message);
            }
        }
    }
    

    /**
     * 실행 중인 큐에서 완료된 사용자들을 완료 큐로 이동시키는 작업 수행.
     */
    @Scheduled(fixedRate = 10000) // 10초마다 실행
    public void finalizeRunningUsers() {
        long expirationTime = Instant.now().toEpochMilli() - 60000; // 1분 후 만료

        // 실행 중인 큐에서 완료된 사용자 검색
        Set<Object> expiredUsers = redisTemplate.opsForZSet().rangeByScore(RUNNING_KEY, 0, expirationTime);

        if (expiredUsers != null && !expiredUsers.isEmpty()) {
            for (Object user : expiredUsers) {
                // 완료된 큐에 사용자 추가
                redisTemplate.opsForZSet().add(FINISHED_KEY, user, expirationTime);

                // 실행 중인 큐에서 사용자 제거
                redisTemplate.opsForZSet().remove(RUNNING_KEY, user);
            }
        }

        broadcastQueueStatus();
    }

    /**
     * 대기열, 실행 중 큐, 완료 큐의 상태를 로깅.
     */
    private void broadcastQueueStatus() {
        long waitingCount = redisTemplate.opsForZSet().size(WAITING_KEY);
        long runningCount = redisTemplate.opsForZSet().size(RUNNING_KEY);
        long finishedCount = redisTemplate.opsForZSet().size(FINISHED_KEY);

        webSocketHandler.broadcastQueueStatus(waitingCount, runningCount, finishedCount);

        System.out.println("\nQueue Status:");
        System.out.println("Waiting Queue: " + waitingCount);
        System.out.println("Running Queue: " + runningCount);
        System.out.println("Finished Queue: " + finishedCount);
    }

    /**
     * 처리 가능한 최대 사용자 수를 반환 (예: 초당 유입량 기반 설정 가능).
     * @return 최대 처리 가능한 사용자 수
     */
    private long getMaxUsersToProcess() {
        return 10; // 예: 초당 최대 10명 처리 가능
    }

    /**
     * 사용자 등록 (테스트용)
     * @param userId 사용자 ID
     */
    public void registerUser(String userId) {
        long currentTime = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().add(WAITING_KEY, userId, currentTime);
    }

    /**
     * 사용자 상태 조회
     * @return 사용자 상태 정보
     */
    public String getQueueStatus() {
        long waitingCount = redisTemplate.opsForZSet().size(WAITING_KEY);
        long runningCount = redisTemplate.opsForZSet().size(RUNNING_KEY);
        long finishedCount = redisTemplate.opsForZSet().size(FINISHED_KEY);

        return String.format("Waiting: %d, Running: %d, Finished: %d", waitingCount, runningCount, finishedCount);
    }
}

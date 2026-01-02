package com.card.Yugioh.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class WebAPIService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String WAITING_KEY = "queue:waiting";
    private static final String RUNNING_KEY = "queue:running";

    public WebAPIService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 사용자 대기열 등록
     * @param userId 사용자 ID
     */
    public void registerUser(String userId) {
        long currentTime = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().add(WAITING_KEY, userId, currentTime);
    }

    /**
     * 대기열 상태 조회
     * @return 대기열 사용자 목록
     */
    public Set<ZSetOperations.TypedTuple<Object>> getQueueStatus() {
        return redisTemplate.opsForZSet().rangeWithScores(WAITING_KEY, 0, -1);
    }

    /**
     * 사용자 대기열 순번 조회
     * @param userId 사용자 ID
     * @return 대기열 순번
     */
    public Long getUserPosition(String userId) {
        Long position = redisTemplate.opsForZSet().rank(WAITING_KEY, userId);
        return position != null ? position + 1 : null;
    }
    
    public boolean isInQueue(String userId) {
        return redisTemplate.opsForZSet().rank(WAITING_KEY, userId) != null;
    }
    
    public boolean isInRunning(String userId) {
        return redisTemplate.opsForZSet().rank(RUNNING_KEY, userId) != null;
    }
    

    /**
     * 대기열에서 사용자 제거
     * @param userId 사용자 ID
     */
    public void removeUserFromQueue(String userId) {
        redisTemplate.opsForZSet().remove(WAITING_KEY, userId);
    }

    /**
     * 대기열 자동 처리
     */
    public void processQueue(long threshold) {
        long currentTime = Instant.now().toEpochMilli();
        Set<Object> users = redisTemplate.opsForZSet().range(WAITING_KEY, 0, threshold - 1);

        if (users != null) {
            for (Object user : users) {
                redisTemplate.opsForZSet().add(RUNNING_KEY, user, currentTime);
                redisTemplate.opsForZSet().remove(WAITING_KEY, user);
            }
        }
    }
}

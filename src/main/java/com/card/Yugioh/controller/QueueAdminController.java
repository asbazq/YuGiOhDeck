package com.card.Yugioh.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import com.card.Yugioh.ScheduledTasks;
import com.card.Yugioh.security.QueueConfig;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/queue")
@RequiredArgsConstructor
public class QueueAdminController {

    private final RedisTemplate<String, String> redis;
    private final ScheduledTasks tasks;

    /** 현재 설정 조회 */
    @GetMapping("/{qid}")
    public QueueConfig getConfig(@PathVariable String qid) {
        Map<Object,Object> m = redis.opsForHash().entries("config:" + qid);
        Map<Object,Object> g = redis.opsForHash().entries("config:global");
        m.putAll(g);
        return QueueConfig.from(m);
    }

     /** throughput, sessionTtlMillis, maxRunning 동시 갱신 */
    @PostMapping("/{qid}")
    public void updateConfig(@PathVariable String qid,
                             @RequestParam(required = false) Integer throughput,
                             @RequestParam(required = false) Long sessionTtlMillis,
                             @RequestParam(required = false) Integer maxRunning) {
        if (throughput != null) {
            redis.opsForHash().put("config:" + qid, "throughput", throughput.toString());
        }
        if (sessionTtlMillis != null) {
            redis.opsForHash().put("config:" + qid, "sessionTtlMillis", sessionTtlMillis.toString());
        }
        if (maxRunning != null) {
            redis.opsForHash().put("config:global", "maxRunning", maxRunning.toString());
        }
    }

    @PostMapping("/fetchApiData")
    public void fetchApiData() {
        tasks.fetchApiData();
    }

    @PostMapping("/fetchLimitData")
    public void fetchLimitData() {
        tasks.fetchLimitData();
    }

    @PostMapping("/fetchKorData")
    public void fetchKorData() {
        tasks.fetchKorData();
    }
}

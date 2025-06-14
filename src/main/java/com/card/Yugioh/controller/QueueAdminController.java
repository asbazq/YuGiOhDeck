package com.card.Yugioh.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import com.card.Yugioh.security.QueueConfig;

import java.util.Map;

@RestController
@RequestMapping("/admin/queue")
@RequiredArgsConstructor
public class QueueAdminController {

    private final RedisTemplate<String, String> redis;

    /** 현재 설정 조회 */
    @GetMapping("/{qid}")
    public QueueConfig getConfig(@PathVariable String qid) {
        Map<Object,Object> m = redis.opsForHash().entries("config:" + qid);
        return QueueConfig.from(m);
    }

    /** throughput, sessionTtlMillis 동시 갱신 */
    @PostMapping("/{qid}")
    public void updateConfig(@PathVariable String qid,
                             @RequestParam(required = false) Integer throughput,
                             @RequestParam(required = false) Long sessionTtlMillis) {
        if (throughput != null) {
            redis.opsForHash().put("config:" + qid, "throughput", throughput.toString());
        }
        if (sessionTtlMillis != null) {
            redis.opsForHash().put("config:" + qid, "sessionTtlMillis", sessionTtlMillis.toString());
        }
    }
}

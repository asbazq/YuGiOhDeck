package com.card.Yugioh.security;

import java.util.Map;

public record QueueConfig(int throughput, long sessionTtlMillis, int maxRunning) {

    /** Redis HASH → QueueConfig 변환 (기본값: throughput=10, TTL=30sec) */
    public static QueueConfig from(Map<Object,Object> m) {
        int tp = 10;
        long ttl = 30 * 1000L;
        int max = 50;
        if (m.containsKey("throughput")) {
            tp = Integer.parseInt((String)m.get("throughput"));
        }
        if (m.containsKey("sessionTtlMillis")) {
            ttl = Long.parseLong((String)m.get("sessionTtlMillis"));
        }
        if (m.containsKey("maxRunning")) {
            max = Integer.parseInt((String)m.get("maxRunning"));
        }
        return new QueueConfig(tp, ttl, max);
    }
}

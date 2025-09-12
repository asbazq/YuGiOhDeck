package com.card.Yugioh.security;

import java.util.Map;

public record QueueConfig(
    int  throughputSite,
    long sessionTtlMillisSite,
    int  throughputPredict,
    long sessionTtlMillisPredict,
    int  maxRunningSite,
    int  maxRunningPredict
) {
    /** 
     * Redis HASH 두 개( site / predict )를 받아 올인원 DTO로 변환.
     * 기본값: throughput=10, TTL=30_000ms, maxRunning=30
     */
    public static QueueConfig from(Map<Object,Object> site,
                                   Map<Object,Object> predict) {
        int  tpSite   = parseIntSafe(get(site,    "throughput"),        10);
        long ttlSite  = parseLongSafe(get(site,   "sessionTtlMillis"),  30_000L);
        int  capSite  = parseIntSafe(get(site,    "maxRunning"),        30);

        int  tpPred   = parseIntSafe(get(predict, "throughput"),        10);
        long ttlPred  = parseLongSafe(get(predict,"sessionTtlMillis"),  30_000L);
        int  capPred  = parseIntSafe(get(predict, "maxRunning"),        30);

        return new QueueConfig(tpSite, ttlSite, tpPred, ttlPred, capSite, capPred);
    }

    private static Object get(Map<Object,Object> m, String k) {
        return (m == null) ? null : m.get(k);
    }
    private static int parseIntSafe(Object v, int def) {
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignore) { return def; }
    }
    private static long parseLongSafe(Object v, long def) {
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignore) { return def; }
    }
}

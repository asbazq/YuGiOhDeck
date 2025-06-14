package com.card.Yugioh.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.card.Yugioh.security.QueueConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueJobService {

    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;
    private final ObjectMapper om = new ObjectMapper();

    private static final String WAITING_PREFIX = "waiting:";
    private static final String RUNNING_PREFIX = "running:";

    @Scheduled(fixedRate = 10_000)
    public void expire() {
        redis.keys(RUNNING_PREFIX + "*").forEach(runKey -> {
            String qid = runKey.substring(RUNNING_PREFIX.length());
            String waitKey = WAITING_PREFIX + qid;
            // log.info("qid = {} waitKey = {}", qid, waitKey);
            long cutoff = System.currentTimeMillis()
                           - QueueConfig.from(redis.opsForHash().entries("config:" + qid))
                                        .sessionTtlMillis();

            // 만료 대상 조회
            Set<String> expired = redis.opsForZSet().rangeByScore(runKey, 0, cutoff);
            if (expired == null || expired.isEmpty()) return;

            // 세션 만료 알림
            expired.forEach(uid ->
                notifier.sendToUser(uid, "{\"type\":\"TIMEOUT\"}"));

            // 파이프라인으로 한번에 이동
            redis.executePipelined((RedisCallback<Void>) conn -> {
                byte[] run = runKey.getBytes();
                for (String uid : expired) {
                    byte[] m = uid.getBytes();
                    conn.zRem(run, m);
                    // log.info("user expired {} : {}", runKey, uid);
                }
                return null;
            });

            int vacancy = expired.size();
            if (vacancy > 0) {
                Set<String> vipBatch = redis.opsForZSet().range(WAITING_PREFIX + "vip", 0, vacancy - 1);
                int vipUser = (vipBatch != null) ? vipBatch.size() : 0;

                Set<String> normBatch = Set.of();
                if (vipUser < vacancy) {
                    normBatch = redis.opsForZSet()
                        .range(WAITING_PREFIX + "main", 0, (vacancy - vipUser) - 1);
                }

                LinkedHashSet<String> promote = new LinkedHashSet<>();
                if (vipBatch != null) promote.addAll(vipBatch);
                promote.addAll(normBatch);

                // Set<String> promote = redis.opsForZSet().range(waitKey, 0, vacancy - 1);
                //  if (promote != null && !promote.isEmpty()) {
                    long now = System.currentTimeMillis();
                    redis.executePipelined((RedisCallback<Void>) conn -> {
                        byte[] vip  = (WAITING_PREFIX + "vip").getBytes();
                        byte[] wait = waitKey.getBytes();
                        byte[] run  = runKey.getBytes();
                        for (String uid : promote) {
                            byte[] m = uid.getBytes();
                            if (vipBatch != null && vipBatch.contains(uid)) {
                                conn.zRem(vip, m);
                            } else {
                                conn.zRem(wait, m);
                            }
                            conn.zAdd(run, now, uid.getBytes());
                            // log.info("Promote {} -> {} ({} queue)", uid, runKey, vipBatch.contains(uid)?"vip":"main");
                        }
                        return null;
                    });
                    promote.forEach(uid -> notifier.sendToUser(uid, "{\"type\":\"ENTER\"}"));
                    broadcastStatus("vip");
                    broadcastStatus("main");
            }
        });
    }

     private void broadcastStatus(String qid) {
        long runningCnt = size(RUNNING_PREFIX + qid);
        long vipCnt = size(WAITING_PREFIX + "vip");
        long mainCnt = size(WAITING_PREFIX + "main");
        long totalWaiting = vipCnt + mainCnt;

        String waitKey = WAITING_PREFIX + qid;
        Set<String> waiters = redis.opsForZSet().range(waitKey, 0, -1);
        if (waiters == null) return;
        for (String uid : waiters) {
            Long rankObj = redis.opsForZSet().rank(waitKey, uid);
            long rank = rankObj == null ? 0L : rankObj + 1;
            long pos = qid.equals("main") ? vipCnt + rank : rank;

            ObjectNode msg = om.createObjectNode()
                    .put("type", "STATUS")
                    .put("qid", qid)
                    .put("running", runningCnt)
                    .put("waitingVip", vipCnt)
                    .put("waitingMain", mainCnt)
                    .put("waiting", totalWaiting)
                    .put("pos", pos);
            notifier.sendToUser(uid, msg.toString());
        }
    }

    private long size(String key) {
        Long v = redis.opsForZSet().size(key);
        return v == null ? 0L : v;
    }

    private String statusJson(String qid) {
        long w = redis.opsForZSet().size(WAITING_PREFIX + qid);
        long r = redis.opsForZSet().size(RUNNING_PREFIX + qid);
        return String.format("{\"queue\":\"%s\",\"waiting\":%d,\"running\":%d}", qid, w, r);
    }
}

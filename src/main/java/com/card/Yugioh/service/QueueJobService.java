package com.card.Yugioh.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.card.Yugioh.security.QueueConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
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

    @Scheduled(fixedRate = 10_000, initialDelayString = "20000")
    public void expire() {
        redis.keys(RUNNING_PREFIX + "*").forEach(runKey -> {
            String qid = runKey.substring(RUNNING_PREFIX.length());
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
                List<TypedTuple<String>> vipList = new ArrayList<>(redis.opsForZSet()
                        .rangeWithScores(WAITING_PREFIX + "vip", 0, vacancy - 1));
                List<TypedTuple<String>> mainList = new ArrayList<>(redis.opsForZSet()
                        .rangeWithScores(WAITING_PREFIX + "main", 0, vacancy - 1));

                List<String> promoteVip = new ArrayList<>();
                List<String> promoteMain = new ArrayList<>();

                int vi = 0, mi = 0;
                while ((promoteVip.size() + promoteMain.size()) < vacancy &&
                       (vi < vipList.size() || mi < mainList.size())) {
                    double vs = vi < vipList.size()
                            ? vipList.get(vi).getScore() - QueueService.VIP_PRIORITY_BONUS
                            : Double.MAX_VALUE;
                    double ms = mi < mainList.size() ? mainList.get(mi).getScore() : Double.MAX_VALUE;

                    if (vs <= ms) {
                        promoteVip.add(vipList.get(vi).getValue());
                        vi++;
                    } else {
                        promoteMain.add(mainList.get(mi).getValue());
                        mi++;
                    }
                }
                long now = System.currentTimeMillis();
                redis.executePipelined((RedisCallback<Void>) conn -> {
                    byte[] vip  = (WAITING_PREFIX + "vip").getBytes();
                    byte[] main = (WAITING_PREFIX + "main").getBytes();
                    byte[] run  = runKey.getBytes();
                    for (String uid : promoteVip) {
                        byte[] m = uid.getBytes();
                        conn.zRem(vip, m);
                        conn.zAdd(run, now, m);
                    }
                    for (String uid : promoteMain) {
                        byte[] m = uid.getBytes();
                        conn.zRem(main, m);
                        conn.zAdd(run, now, m);
                    }
                    return null;
                });

                promoteVip.forEach(uid -> notifier.sendToUser(uid, "{\"type\":\"ENTER\"}"));
                promoteMain.forEach(uid -> notifier.sendToUser(uid, "{\"type\":\"ENTER\"}"));
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
}

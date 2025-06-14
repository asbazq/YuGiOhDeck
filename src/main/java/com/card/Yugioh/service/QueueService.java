package com.card.Yugioh.service;

import com.card.Yugioh.security.UserDisconnectedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class QueueService {

    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;

    private static final String RUNNING_PREFIX = "running:";
    private static final String WAITING_PREFIX = "waiting:";
    private static final int    MAX_runnging = 2;

    private final ObjectMapper om = new ObjectMapper();

    public QueueService(RedisTemplate<String, String> redis,
                        QueueNotifier notifier) {
        this.redis    = redis;
        this.notifier = notifier;
    }

    /* ======================= 입장 ======================= */
    public QueueResponse enter(String qid, String user) {
        String runKey  = RUNNING_PREFIX + qid;
        String waitKey = WAITING_PREFIX + qid;
        
        if (redis.opsForZSet().score(runKey, user) != null)
            return entered();

        if (totalRunningSize() < MAX_runnging) {
            redis.opsForZSet().add(runKey, user, Instant.now().toEpochMilli());
            broadcastStatus(qid);
            if (qid.equals("vip")) broadcastStatus("main");
            // log.info("enter : {}", user);
            // log.info("입장 수 : {}", totalRunningSize());
            // log.info("main 입장 수 : {}", size(RUNNING_PREFIX + "main"));
            // log.info("vip 입장 수 : {}", size(RUNNING_PREFIX + "vip"));
            return entered();
        }
        redis.opsForZSet().add(waitKey, user, Instant.now().toEpochMilli());
        broadcastStatus(qid);
        if (qid.equals("vip")) broadcastStatus("main");
        // log.info("queue " + user);

        return waiting(absolutePosition(qid, user));
    }

    /* ======================= 퇴장 ======================= */
    public void leave(String qid, String user) {
        if (user == null || user.isBlank()) return;

        String runKey  = RUNNING_PREFIX + qid;
        String vipKey  = WAITING_PREFIX + "vip";
        String mainKey = WAITING_PREFIX + "main";
        
        redis.opsForZSet().remove(runKey, user);
        boolean waitingRemoved = redis.opsForZSet().remove(vipKey, user) == 1;
        if (!waitingRemoved) redis.opsForZSet().remove(mainKey, user);

        promoteNextUser(qid);
        broadcastStatus(qid);
        if (qid.equals("vip")) {
            broadcastStatus("main");
        } else if (qid.equals("main")) {
            broadcastStatus("vip");
        }
        // log.info("leave " + user);
    }

    /* =================== WS 연결 종료 =================== */
    @EventListener
    public void onWebsocketDisconnected(UserDisconnectedEvent ev) {
        if (ev.userId() != null) leave(ev.qid(), ev.userId());
    }

    /* ====================== 상태 ======================= */
    public QueueStatus status(String qid) {
        String vipRunKey  = RUNNING_PREFIX + "vip";
        String mainRunKey = RUNNING_PREFIX + "main";
        String vipWaitKey  = WAITING_PREFIX + "vip";
        String mainWaitKey = WAITING_PREFIX + "main";

        long runSize = size(vipRunKey) + size(mainRunKey);
        long waitSize = size(vipWaitKey) + size(mainWaitKey);
        
        return new QueueStatus(runSize, waitSize, size(vipWaitKey), size(mainWaitKey));
    }

    public Map<String, Long> QueuePosition(String qid, String userId) {
        long pos = absolutePosition(qid, userId);
        return Map.of("pos", pos);
    }


    /* ===================== 내부 로직 ==================== */
    private void promoteNextUser(String qid) {
        String runKey  = RUNNING_PREFIX + qid;
        String vipKey  = WAITING_PREFIX + "vip";
        String mainKey = WAITING_PREFIX + "main";
        if (totalRunningSize() >= MAX_runnging) return;

        // 1) VIP 대기자 우선 승격
        Set<String> vipBatch = redis.opsForZSet().range(vipKey, 0, 0);
        if (vipBatch != null && !vipBatch.isEmpty()) {
            String uid = vipBatch.iterator().next();
            redis.opsForZSet().remove(vipKey, uid);
            redis.opsForZSet().add(runKey, uid, Instant.now().toEpochMilli());
            notifier.sendToUser(uid, "{\"type\":\"ENTER\"}");
            return;
        }
        // 2) 일반 대기자 승격
        Set<String> mainBatch = redis.opsForZSet().range(mainKey, 0, 0);
        if (mainBatch != null && !mainBatch.isEmpty()) {
            String uid = mainBatch.iterator().next();
            redis.opsForZSet().remove(mainKey, uid);
            redis.opsForZSet().add(runKey, uid, Instant.now().toEpochMilli());
            notifier.sendToUser(uid, "{\"type\":\"ENTER\"}");
        }
    }

    private void broadcastStatus(String qid) {
        try {
             // 1) 현재 상태 집계
            long runningCnt = size(RUNNING_PREFIX + qid);
            long vipCnt = size(WAITING_PREFIX + "vip");
            long mainCnt = size(WAITING_PREFIX + "main");
            long totalWaiting = vipCnt + mainCnt;

            // 2) 이 큐의 대기자 리스트
            String waitKey = WAITING_PREFIX + qid;
            Set<String> waiters = redis.opsForZSet().range(waitKey, 0, -1);
            if (waiters == null) return;

            // 3) 각 사용자에게 개별 STATUS+pos 전송
            for (String uid : waiters) {
                // ZSET 내 0-based rank → 1-based 순번
                Long rank = redis.opsForZSet().rank(waitKey, uid);
                long localRank = (rank == null ? 0L : rank) + 1;

                // main 큐라면 VIP 수 보정
                long pos = qid.equals("main")
                         ? vipCnt + localRank
                         : localRank;

                // JSON 생성
                ObjectNode node = om.createObjectNode()
                    .put("type",        "STATUS")
                    .put("qid",         qid)
                    .put("running",     runningCnt)
                    .put("waitingVip",  vipCnt)
                    .put("waitingMain", mainCnt)
                    .put("waiting",     totalWaiting)
                    .put("pos",         pos);

                // 개인 세션으로 전송
                notifier.sendToUser(uid, node.toString());
            }
        } catch (Exception e) {
            log.error("broadcast fail", e);
        }
    }

    /* ================== 보조 메서드 =================== */
    private long size(String key) {
        Long v = redis.opsForZSet().size(key);
        return v == null ? 0 : v;
    }

    private long totalRunningSize() {
        // 1) running:* 패턴에 맞는 모든 키 가져오기
        Set<String> keys = redis.keys(RUNNING_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return 0L;

        // 2) 각 ZSET 크기를 합산
        long total = 0;
        for (String k : keys) {
            Long sz = redis.opsForZSet().size(k);
            total += (sz == null ? 0L : sz);
        }
        return total;
    }

    private long position(String waitKey, String user) {
        Long r = redis.opsForZSet().rank(waitKey, user);
        return r == null ? -1 : r + 1;
    }

    // 절대 순번 계산 (main 큐는 VIP 대기열 보정)
    private long absolutePosition(String qid, String user) {
        String waitKey = WAITING_PREFIX + qid;
        long rank = position(waitKey, user);
        if (rank < 0) return -1;
        if ("main".equals(qid)) {
            Long vipObj = redis.opsForZSet().size(WAITING_PREFIX + "vip");
            long vipCnt = vipObj == null ? 0L : vipObj;
            return vipCnt + rank;
        }
        return rank;
    }

    /* ===================== DTO ====================== */
    public record QueueStatus(long running, long waiting, long waitingVip, long waitingMain) {}
    public record QueueResponse(boolean entered, long position) {}
    private QueueResponse entered()            { return new QueueResponse(true, 0); }
    private QueueResponse waiting(long pos)    { return new QueueResponse(false, pos); }
}

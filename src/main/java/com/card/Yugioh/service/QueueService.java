package com.card.Yugioh.service;

import com.card.Yugioh.security.QueueConfig;
import com.card.Yugioh.security.UserDisconnectedEvent;
import com.card.Yugioh.security.UserPingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class QueueService {

    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;

    private static final String RUNNING_PREFIX = "running:";
    private static final String WAITING_PREFIX = "waiting:";
    public static final long VIP_PRIORITY_BONUS = 30_000L;

    private final ObjectMapper om = new ObjectMapper();

    public QueueService(RedisTemplate<String, String> redis,
                        QueueNotifier notifier) {
        this.redis    = redis;
        this.notifier = notifier;
    }

    /* ======================= 입장 ======================= */
    public QueueResponse enter(String group, String qid, String user) {
        String runKey  = runKey(group, qid);
        String waitKey = waitKey(group, qid);

        if (redis.opsForZSet().score(runKey, user) != null)
            return entered();

        // 공정성: 대기열이 존재하면 바로 running 으로 입장하지 않고 대기열에 넣는다
        long totalWaiting = size(waitKey(group, "vip")) + size(waitKey(group, "main"));
        boolean hasWaiting = totalWaiting > 0;

        if (totalRunningSize(group) < maxRunning(group) && !hasWaiting) {
            redis.opsForZSet().add(runKey, user, Instant.now().toEpochMilli());
            broadcastStatus(group, qid);
            if ("vip".equals(qid)) broadcastStatus(group, "main");
            return entered();
        }
        double score = enqScore(group, "vip".equals(qid));
        redis.opsForZSet().add(waitKey, user, score);
        broadcastStatus(group, qid);
        if ("vip".equals(qid)) broadcastStatus(group, "main");

        return waiting(absolutePosition(group, qid, user));
    }

    private double enqScore(String group, boolean isVip) {
        long now = System.currentTimeMillis();                   // ms
        Long seq = redis.opsForValue().increment("seq:{" + group + "}", 1); // 단조 증가
        long micro = now * 1000L + ((seq != null ? seq : 0L) % 1000L);     // ms + 소수점
        long vipBias = isVip ? VIP_PRIORITY_BONUS : 0L;  // VIP는 선행 (미세단위와 일관되게)
        return (double) (micro - vipBias);
    }

    /* ======================= 퇴장 ======================= */
    public void leave(String group, String qid, String user) {
        if (user == null || user.isBlank()) return;

        String runKey  = runKey(group, qid);
        String vipKey  = waitKey(group, "vip");
        String mainKey = waitKey(group, "main");

        redis.opsForZSet().remove(runKey, user);
        boolean waitingRemoved = redis.opsForZSet().remove(vipKey, user) == 1;
        if (!waitingRemoved) redis.opsForZSet().remove(mainKey, user);

        promoteNextUser(group, qid); // ← Lua 기반 승격
        broadcastStatus(group, qid);
        if ("vip".equals(qid)) {
            broadcastStatus(group, "main");
        } else if ("main".equals(qid)) {
            broadcastStatus(group, "vip");
        }
    }

    /* ==================== 세션 연장 ==================== */
    public void touch(String group, String qid, String user) {
        String runKey = runKey(group, qid);
        if (redis.opsForZSet().score(runKey, user) != null) {
            redis.opsForZSet().add(runKey, user, Instant.now().toEpochMilli());
        }
    }

    /* PING 이벤트 수신 시 세션 TTL 갱신 */
    @EventListener
    public void onUserPing(UserPingEvent ev) {
        touch(ev.group(), ev.qid(), ev.userId());
    }

    /* =================== WS 연결 종료 =================== */
    @EventListener
    public void onWebsocketDisconnected(UserDisconnectedEvent ev) {
        if (ev.userId() != null) leave(ev.group(), ev.qid(), ev.userId());
    }

    /* ====================== 상태 ======================= */
    public QueueStatus status(String group) {
        long runSize  = size(runKey(group, "vip")) + size(runKey(group, "main"));
        long vipWait  = size(waitKey(group, "vip"));
        long mainWait = size(waitKey(group, "main"));
        long waitSize = vipWait + mainWait;
        return new QueueStatus(runSize, waitSize, vipWait, mainWait);
    }

    public Map<String, Long> queuePosition(String group, String qid, String userId) {
        return Map.of("pos", absolutePosition(group, qid, userId));
    }

    public Map<String, Object> isRunning(String group, String qid, String userId) {
        boolean running = (redis.opsForZSet().score(runKey(group, qid), userId) != null);
        long pos = running ? 0L : absolutePosition(group, qid, userId);
        // Map.of는 제네릭 타입 맞춰야함 (String,Object)
        return Map.of("running", running, "pos", pos);
    }

    private int maxRunning(String group) {
        Object v = redis.opsForHash().get("config:{" + group + "}", "maxRunning");
        try {
            int n = (v == null) ? 30 : Integer.parseInt(String.valueOf(v));
            return Math.max(1, n);
        } catch (Exception e) {
            return 30;
        }
    }

    /* ===================== Lua 승격 스크립트 ==================== */
    private static final String LUA_PROMOTE_WITH_CAP = """
    -- KEYS: 1 vipWaitKey, 2 mainWaitKey, 3 vipRunKey, 4 mainRunKey, 5 targetRunKey, 6 configKey
    -- ARGV: 1 nowMs, 2 fallbackMaxRunning
    local vipWaitKey   = KEYS[1]
    local mainWaitKey  = KEYS[2]
    local vipRunKey    = KEYS[3]
    local mainRunKey   = KEYS[4]
    local targetRunKey = KEYS[5]
    local configKey    = KEYS[6]

    local nowMs  = tonumber(ARGV[1])
    local fmax   = tonumber(ARGV[2]) or 1

    local m = redis.call('HGET', configKey, 'maxRunning')
    local maxRunning = tonumber(m) or fmax
    if not maxRunning or maxRunning < 1 then maxRunning = 1 end

    local function totalRunning()
    return (redis.call('ZCARD', vipRunKey) or 0) + (redis.call('ZCARD', mainRunKey) or 0)
    end

    if totalRunning() >= maxRunning then
    return nil
    end

    -- target이 vip면 VIP 대기 우선, main이면 MAIN 대기 우선
    local primaryWait   = (targetRunKey == vipRunKey) and vipWaitKey  or mainWaitKey
    local secondaryWait = (targetRunKey == vipRunKey) and mainWaitKey or vipWaitKey

    local popped = redis.call('ZPOPMIN', primaryWait, 1)
    if (not popped) or (#popped == 0) then
    popped = redis.call('ZPOPMIN', secondaryWait, 1)
    end
    if not popped or #popped == 0 then
    return nil
    end

    local uid = popped[1]
    redis.call('ZADD', targetRunKey, nowMs, uid)
    return uid
    """;


    private final RedisScript<String> promoteScript =
            new DefaultRedisScript<>(LUA_PROMOTE_WITH_CAP, String.class);

    /* ===================== 내부 로직 ==================== */
    private void promoteNextUser(String group, String qid) {
        final String vipWait  = waitKey(group, "vip");
        final String mainWait = waitKey(group, "main");
        final String vipRun   = runKey(group, "vip");
        final String mainRun  = runKey(group, "main");
        final String targetRun= runKey(group, qid);
        final String config   = cfgKey(group);

        String now = String.valueOf(Instant.now().toEpochMilli());
        int fallbackMax = Math.max(1, maxRunning(group));

        String uid = redis.execute(
                promoteScript,
                List.of(vipWait, mainWait, vipRun, mainRun, targetRun, config),
                now, String.valueOf(fallbackMax)
        );

        if (uid != null && !uid.isBlank()) {
            notifier.sendToUser(group, uid, "{\"type\":\"ENTER\"}");
        }
    }

    private void broadcastStatus(String group, String qid) {
        try {
            long runningCnt = size(runKey(group, qid));
            long vipCnt     = size(waitKey(group, "vip"));
            long mainCnt    = size(waitKey(group, "main"));
            long totalWaiting = vipCnt + mainCnt;

            String wKey = waitKey(group, qid);
            Set<String> waiters = redis.opsForZSet().range(wKey, 0, -1);
            if (waiters == null) return;

            for (String uid : waiters) {
                Long rank = redis.opsForZSet().rank(wKey, uid);
                long localRank = (rank == null ? 0L : rank) + 1;
                long pos = "main".equals(qid) ? vipCnt + localRank : localRank;

                ObjectNode node = om.createObjectNode()
                    .put("type",        "STATUS")
                    .put("group",       group)
                    .put("qid",         qid)
                    .put("running",     runningCnt)
                    .put("waitingVip",  vipCnt)
                    .put("waitingMain", mainCnt)
                    .put("waiting",     totalWaiting)
                    .put("pos",         pos);

                notifier.sendToUser(group, uid, node.toString());
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

    private long totalRunningSize(String group) {
        // 그룹 내에서만 합산 (KEEYS 제거)
        return size(runKey(group, "vip")) + size(runKey(group, "main"));
    }

    private long position(String waitKey, String user) {
        Long r = redis.opsForZSet().rank(waitKey, user);
        return r == null ? -1 : r + 1;
    }

    // 절대 순번 계산 (main 큐는 VIP 대기열 보정)
    private long absolutePosition(String group, String qid, String user) {
        String wKey = waitKey(group, qid);
        long rank = position(wKey, user);
        if (rank < 0) return -1;
        if ("main".equals(qid)) {
            long vipCnt = size(waitKey(group, "vip"));
            return vipCnt + rank;
        }
        return rank;
    }

    private static String slot(String group) {
        return (group == null || group.isBlank()) ? "" : "{" + group + "}:";
    }
    private static String runKey(String group, String qid) {
        return RUNNING_PREFIX + slot(group) + qid;    // e.g. running:{site}:main
    }
    private static String waitKey(String group, String qid) {
        return WAITING_PREFIX + slot(group) + qid;    // e.g. waiting:{site}:vip
    }
    private static String cfgKey(String group) {
        return "config:" + slot(group).replace(":{", "{"); // -> "config:{site}"
    }

    /* ===================== DTO ====================== */
    public record QueueStatus(long running, long waiting, long waitingVip, long waitingMain) {}
    public record QueueResponse(boolean entered, long position) {}
    private QueueResponse entered()         { return new QueueResponse(true, 0); }
    private QueueResponse waiting(long pos) { return new QueueResponse(false, pos); }
}

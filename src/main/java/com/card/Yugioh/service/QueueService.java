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
    public static final long VIP_PRIORITY_BONUS = 3_000_000L;
    private static final int VIP_STREAK_LIMIT = 3;

    private final ObjectMapper om = new ObjectMapper();

    public QueueService(RedisTemplate<String, String> redis,
                        QueueNotifier notifier) {
        this.redis    = redis;
        this.notifier = notifier;
    }

    /* ======================= 입장 (Lua 원자화) ======================= */
    /**
     * enter()는 자리 판단 → 즉시입장/대기삽입 → 절대순번 계산까지
     * 단일 Lua(EVAL)로 처리하여 레이스 윈도우 제거
     */
    public QueueResponse enter(String group, String qid, String user) {
        final String vipRun  = runKey(group, "vip");
        final String mainRun = runKey(group, "main");
        final String runKey  = runKey(group, qid);
        final String vipWait = waitKey(group, "vip");
        final String mainWait= waitKey(group, "main");
        final String cfgKey  = cfgKey(group);
        final String seqKey  = "seq:{" + group + "}";

        long now = System.currentTimeMillis();
        int  fallbackMax = Math.max(1, maxRunning(group));
        boolean isVip = "vip".equals(qid);

        @SuppressWarnings("unchecked")
        List<Object> res = (List<Object>) redis.execute(
            enterScript,
            List.of(vipRun, mainRun, runKey, vipWait, mainWait, cfgKey, seqKey),
            String.valueOf(now),
            String.valueOf(fallbackMax),
            user,
            isVip ? "1" : "0",
            String.valueOf(VIP_PRIORITY_BONUS)
        );

        // 실패/비정상 방어
        if (res == null || res.size() < 2) {
            // 안전하게 대기 취급하고 상태 갱신
            broadcastStatus(group, qid);
            if (isVip) broadcastStatus(group, "main");
            return waiting(-1);
        }

        String state = String.valueOf(res.get(0)); // "entered" | "waiting"
        long pos = 0;
        try {
            pos = Long.parseLong(String.valueOf(res.get(1)));
        } catch (Exception ignore) {}

        // 입장/대기 상태에 따라 브로드캐스트
        broadcastStatus(group, qid);
        if (isVip) broadcastStatus(group, "main");

        if ("entered".equals(state)) {
            return entered();
        } else {
            return waiting(pos);
        }
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
    -- ARGV: 1 nowMs, 2 fallbackMaxRunning, 3 vipStreakLimit
    local vipWaitKey   = KEYS[1]
    local mainWaitKey  = KEYS[2]
    local vipRunKey    = KEYS[3]
    local mainRunKey   = KEYS[4]
    local targetRunKey = KEYS[5]
    local configKey    = KEYS[6]

    local nowMs  = tonumber(ARGV[1])
    local fmax   = tonumber(ARGV[2]) or 1
    local limit  = tonumber(ARGV[3]) or 3

    local m = redis.call('HGET', configKey, 'maxRunning')
    local maxRunning = tonumber(m) or fmax
    if not maxRunning or maxRunning < 1 then maxRunning = 1 end

    local function totalRunning()
      return (redis.call('ZCARD', vipRunKey) or 0) + (redis.call('ZCARD', mainRunKey) or 0)
    end

    if totalRunning() >= maxRunning then
      return nil
    end

    -- 연속 VIP 카운터 읽기 (없으면 0)
    local vipStreak = tonumber(redis.call('HGET', configKey, 'vip_streak')) or 0

    -- 기본 우선 순위: targetRunKey가 vip면 vip 대기 우선, main이면 main 대기 우선
    local primaryWait   = (targetRunKey == vipRunKey) and vipWaitKey  or mainWaitKey
    local secondaryWait = (targetRunKey == vipRunKey) and mainWaitKey or vipWaitKey

    local waitVip  = redis.call('ZCARD', vipWaitKey)  or 0
    local waitMain = redis.call('ZCARD', mainWaitKey) or 0

    -- ★ 연속 VIP 제한: VIP 슬롯에 넣는 상황에서 vipStreak가 limit 이상이면 main을 우선 pop
    if (targetRunKey == vipRunKey) and (vipStreak >= limit) and (waitMain > 0) then
    primaryWait, secondaryWait = mainWaitKey, vipWaitKey
    end

    -- pop
    local popped = redis.call('ZPOPMIN', primaryWait, 1)
    if (not popped) or (#popped == 0) then
    popped = redis.call('ZPOPMIN', secondaryWait, 1)
    end
    if not popped or #popped == 0 then
    return nil
    end

    local uid = popped[1]
    redis.call('ZADD', targetRunKey, nowMs, uid)

    -- ★ streak 업데이트:
    --   - VIP 슬롯으로 승격했는데 primary가 vip였다 → 연속 VIP +1
    --   - VIP 슬롯인데 primary가 main이었다(강제 끼워넣기) → streak 0으로 리셋
    --   - main 슬롯으로 승격 → streak 0으로 리셋
    if targetRunKey == vipRunKey then
    if primaryWait == vipWaitKey then
        redis.call('HINCRBY', configKey, 'vip_streak', 1)
    else
        redis.call('HSET',    configKey, 'vip_streak', 0)
    end
    else
    redis.call('HSET',      configKey, 'vip_streak', 0)
    end
    return uid
    """;

    private final RedisScript<String> promoteScript =
            new DefaultRedisScript<>(LUA_PROMOTE_WITH_CAP, String.class);

    /* ===================== Lua 입장 스크립트 ==================== */
    /**
     * 반환: {"entered","0"} 또는 {"waiting","<pos>"}
     * - 공정성: 대기 총합>0이면 즉시입장 금지
     * - 이미 대기 중인 사용자는 점수 유지(FIFO)
     * - 절대 순번: main 대기는 VIP 대기 수를 앞에 더함
     */
    private static final String LUA_ENTER = """
    -- KEYS: 1 vipRunKey, 2 mainRunKey, 3 targetRunKey, 4 vipWaitKey, 5 mainWaitKey, 6 configKey, 7 seqKey
    -- ARGV: 1 nowMs, 2 fallbackMaxRunning, 3 userId, 4 isVip(0/1), 5 vipBias

    local vipRunKey    = KEYS[1]
    local mainRunKey   = KEYS[2]
    local targetRunKey = KEYS[3]
    local vipWaitKey   = KEYS[4]
    local mainWaitKey  = KEYS[5]
    local configKey    = KEYS[6]
    local seqKey       = KEYS[7]

    local nowMs   = tonumber(ARGV[1])
    local fmax    = tonumber(ARGV[2]) or 1
    local uid     = ARGV[3]
    local isVip   = tonumber(ARGV[4]) == 1
    local vipBias = tonumber(ARGV[5]) or 0

    local function maxRunning()
      local m = redis.call('HGET', configKey, 'maxRunning')
      local v = tonumber(m) or fmax
      if not v or v < 1 then v = 1 end
      return v
    end

    local function totalRunning()
      return (redis.call('ZCARD', vipRunKey) or 0) + (redis.call('ZCARD', mainRunKey) or 0)
    end

    -- 0) 이미 running이면 즉시 완료
    if redis.call('ZSCORE', targetRunKey, uid) then
      return { "entered", "0" }
    end

    -- 1) 전체 대기 수
    local waitVip  = redis.call('ZCARD', vipWaitKey)  or 0
    local waitMain = redis.call('ZCARD', mainWaitKey) or 0
    local totalWait = waitVip + waitMain

    -- 2) 대기 없고 cap 남아있으면 즉시 입장
    if totalWait == 0 and totalRunning() < maxRunning() then
      redis.call('ZADD', targetRunKey, nowMs, uid)
      return { "entered", "0" }
    end

    -- 3) 대기 삽입 (이미 어느 대기열에 있으면 기존 점수 유지)
    local inVip  = redis.call('ZSCORE', vipWaitKey,  uid)
    local inMain = redis.call('ZSCORE', mainWaitKey, uid)

    local targetWaitKey = isVip and vipWaitKey or mainWaitKey
    if (not inVip) and (not inMain) then
      local seq = redis.call('INCR', seqKey)
      local micro = nowMs * 1000 + (seq % 1000)
      local score = isVip and (micro - vipBias) or micro
      redis.call('ZADD', targetWaitKey, 'NX', score, uid)
    else
      targetWaitKey = inVip and vipWaitKey or mainWaitKey
    end

    -- 4) 절대 순번 계산 (main은 VIP 대기 수 보정)
    local r = redis.call('ZRANK', targetWaitKey, uid)
    if not r then
      return { "waiting", "-1" }
    end
    local localRank = r + 1
    if targetWaitKey == mainWaitKey then
      return { "waiting", tostring(waitVip + localRank) }
    else
      return { "waiting", tostring(localRank) }
    end
    """;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enterScript =
            new DefaultRedisScript<>(LUA_ENTER, List.class);

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
                now, String.valueOf(fallbackMax), String.valueOf(VIP_STREAK_LIMIT)
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

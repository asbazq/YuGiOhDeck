package com.card.Yugioh.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.card.Yugioh.security.UserDisconnectedEvent;
import com.card.Yugioh.security.UserPingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

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

    public QueueService(RedisTemplate<String, String> redis, QueueNotifier notifier) {
        this.redis = redis;
        this.notifier = notifier;
    }

    public QueueResponse enter(String group, String qid, String user) {
        validateGroupAndQid(group, qid);
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        final String vipRun = runKey(group, "vip");
        final String mainRun = runKey(group, "main");
        final String targetRun = runKey(group, qid);
        final String vipWait = waitKey(group, "vip");
        final String mainWait = waitKey(group, "main");
        final String cfgKey = cfgKey(group);
        final String seqKey = "seq:{" + group + "}";

        long now = System.currentTimeMillis();
        int fallbackMax = Math.max(1, maxRunning(group));
        boolean isVip = "vip".equals(qid);

        @SuppressWarnings("unchecked")
        List<Object> res = (List<Object>) redis.execute(
            enterScript,
            List.of(vipRun, mainRun, targetRun, vipWait, mainWait, cfgKey, seqKey),
            String.valueOf(now),
            String.valueOf(fallbackMax),
            user,
            isVip ? "1" : "0",
            String.valueOf(VIP_PRIORITY_BONUS)
        );

        if (res == null || res.size() < 2) {
            broadcastStatus(group, "vip");
            broadcastStatus(group, "main");
            return waiting(-1);
        }

        String state = String.valueOf(res.get(0));
        long pos = 0;
        try {
            pos = Long.parseLong(String.valueOf(res.get(1)));
        } catch (Exception ignore) {
        }

        broadcastStatus(group, "vip");
        broadcastStatus(group, "main");
        return "entered".equals(state) ? entered() : waiting(pos);
    }

    public void leave(String group, String qid, String user) {
        validateGroupAndQid(group, qid);
        if (user == null || user.isBlank()) {
            return;
        }

        boolean removedVipRunning = removeFromZSet(runKey(group, "vip"), user);
        boolean removedMainRunning = removeFromZSet(runKey(group, "main"), user);
        removeFromZSet(waitKey(group, "vip"), user);
        removeFromZSet(waitKey(group, "main"), user);

        if (removedVipRunning) {
            promoteNextUser(group, "vip");
        }
        if (removedMainRunning) {
            promoteNextUser(group, "main");
        }

        broadcastStatus(group, "vip");
        broadcastStatus(group, "main");
    }

    public void touch(String group, String qid, String user) {
        validateGroupAndQid(group, qid);
        if (user == null || user.isBlank()) {
            return;
        }

        long now = Instant.now().toEpochMilli();
        touchRunKey(runKey(group, "vip"), user, now);
        touchRunKey(runKey(group, "main"), user, now);
    }

    @EventListener
    public void onUserPing(UserPingEvent ev) {
        touch(ev.group(), ev.qid(), ev.userId());
    }

    @EventListener
    public void onWebsocketDisconnected(UserDisconnectedEvent ev) {
        if (ev.userId() != null) {
            leave(ev.group(), ev.qid(), ev.userId());
        }
    }

    public QueueStatus status(String group) {
        validateGroup(group);
        long runSize = size(runKey(group, "vip")) + size(runKey(group, "main"));
        long vipWait = size(waitKey(group, "vip"));
        long mainWait = size(waitKey(group, "main"));
        long waitSize = vipWait + mainWait;
        return new QueueStatus(runSize, waitSize, vipWait, mainWait);
    }

    public Map<String, Long> queuePosition(String group, String qid, String userId) {
        validateGroupAndQid(group, qid);
        return Map.of("pos", absolutePosition(group, qid, userId));
    }

    public Map<String, Object> isRunning(String group, String qid, String userId) {
        validateGroupAndQid(group, qid);
        boolean running = isRunningInAnyQueue(group, userId);
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

    local vipStreak = tonumber(redis.call('HGET', configKey, 'vip_streak')) or 0
    local primaryWait   = (targetRunKey == vipRunKey) and vipWaitKey  or mainWaitKey
    local secondaryWait = (targetRunKey == vipRunKey) and mainWaitKey or vipWaitKey

    local waitMain = redis.call('ZCARD', mainWaitKey) or 0
    if (targetRunKey == vipRunKey) and (vipStreak >= limit) and (waitMain > 0) then
      primaryWait, secondaryWait = mainWaitKey, vipWaitKey
    end

    local popped = redis.call('ZPOPMIN', primaryWait, 1)
    if (not popped) or (#popped == 0) then
      popped = redis.call('ZPOPMIN', secondaryWait, 1)
    end
    if not popped or #popped == 0 then
      return nil
    end

    local uid = popped[1]
    redis.call('ZADD', targetRunKey, nowMs, uid)

    if targetRunKey == vipRunKey then
      if primaryWait == vipWaitKey then
        redis.call('HINCRBY', configKey, 'vip_streak', 1)
      else
        redis.call('HSET', configKey, 'vip_streak', 0)
      end
    else
      redis.call('HSET', configKey, 'vip_streak', 0)
    end

    return uid
    """;

    private final RedisScript<String> promoteScript =
        new DefaultRedisScript<>(LUA_PROMOTE_WITH_CAP, String.class);

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

    if redis.call('ZSCORE', vipRunKey, uid) or redis.call('ZSCORE', mainRunKey, uid) then
      return { "entered", "0" }
    end

    local waitVip  = redis.call('ZCARD', vipWaitKey) or 0
    local waitMain = redis.call('ZCARD', mainWaitKey) or 0
    local totalWait = waitVip + waitMain

    if totalWait == 0 and totalRunning() < maxRunning() then
      redis.call('ZADD', targetRunKey, nowMs, uid)
      return { "entered", "0" }
    end

    local inVip  = redis.call('ZSCORE', vipWaitKey, uid)
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

    local r = redis.call('ZRANK', targetWaitKey, uid)
    if not r then
      return { "waiting", "-1" }
    end

    local localRank = r + 1
    if targetWaitKey == mainWaitKey then
      return { "waiting", tostring(waitVip + localRank) }
    end
    return { "waiting", tostring(localRank) }
    """;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enterScript =
        new DefaultRedisScript<>(LUA_ENTER, List.class);

    private void promoteNextUser(String group, String qid) {
        final String vipWait = waitKey(group, "vip");
        final String mainWait = waitKey(group, "main");
        final String vipRun = runKey(group, "vip");
        final String mainRun = runKey(group, "main");
        final String targetRun = runKey(group, qid);
        final String config = cfgKey(group);

        String now = String.valueOf(Instant.now().toEpochMilli());
        int fallbackMax = Math.max(1, maxRunning(group));

        String uid = redis.execute(
            promoteScript,
            List.of(vipWait, mainWait, vipRun, mainRun, targetRun, config),
            now,
            String.valueOf(fallbackMax),
            String.valueOf(VIP_STREAK_LIMIT)
        );

        if (uid != null && !uid.isBlank()) {
            notifier.sendToUser(group, uid, "{\"type\":\"ENTER\"}");
        }
    }

    private void broadcastStatus(String group, String qid) {
        try {
            long runningCnt = size(runKey(group, qid));
            long vipCnt = size(waitKey(group, "vip"));
            long mainCnt = size(waitKey(group, "main"));
            long totalWaiting = vipCnt + mainCnt;

            String waitKey = waitKey(group, qid);
            Set<String> waiters = redis.opsForZSet().range(waitKey, 0, -1);
            if (waiters == null) {
                return;
            }

            for (String uid : waiters) {
                Long rank = redis.opsForZSet().rank(waitKey, uid);
                long localRank = (rank == null ? 0L : rank) + 1;
                long pos = "main".equals(qid) ? vipCnt + localRank : localRank;

                ObjectNode node = om.createObjectNode()
                    .put("type", "STATUS")
                    .put("group", group)
                    .put("qid", qid)
                    .put("running", runningCnt)
                    .put("waitingVip", vipCnt)
                    .put("waitingMain", mainCnt)
                    .put("waiting", totalWaiting)
                    .put("pos", pos);

                notifier.sendToUser(group, uid, node.toString());
            }
        } catch (Exception e) {
            log.error("broadcast fail", e);
        }
    }

    private boolean removeFromZSet(String key, String user) {
        Long removed = redis.opsForZSet().remove(key, user);
        return removed != null && removed > 0;
    }

    private void touchRunKey(String key, String user, long now) {
        if (redis.opsForZSet().score(key, user) != null) {
            redis.opsForZSet().add(key, user, now);
        }
    }

    private boolean isRunningInAnyQueue(String group, String user) {
        return redis.opsForZSet().score(runKey(group, "vip"), user) != null
            || redis.opsForZSet().score(runKey(group, "main"), user) != null;
    }

    private long size(String key) {
        Long v = redis.opsForZSet().size(key);
        return v == null ? 0 : v;
    }

    private long position(String waitKey, String user) {
        Long r = redis.opsForZSet().rank(waitKey, user);
        return r == null ? -1 : r + 1;
    }

    private long absolutePosition(String group, String qid, String user) {
        String waitKey = waitKey(group, qid);
        long rank = position(waitKey, user);
        if (rank < 0) {
            return -1;
        }
        if ("main".equals(qid)) {
            long vipCnt = size(waitKey(group, "vip"));
            return vipCnt + rank;
        }
        return rank;
    }

    private void validateGroupAndQid(String group, String qid) {
        validateGroup(group);
        if (!"vip".equals(qid) && !"main".equals(qid)) {
            throw new IllegalArgumentException("Unsupported qid: " + qid);
        }
    }

    private void validateGroup(String group) {
        if (!"site".equals(group) && !"predict".equals(group)) {
            throw new IllegalArgumentException("Unsupported group: " + group);
        }
    }

    private static String slot(String group) {
        return (group == null || group.isBlank()) ? "" : "{" + group + "}:";
    }

    private static String runKey(String group, String qid) {
        return RUNNING_PREFIX + slot(group) + qid;
    }

    private static String waitKey(String group, String qid) {
        return WAITING_PREFIX + slot(group) + qid;
    }

    private static String cfgKey(String group) {
        return "config:" + slot(group).replace(":{", "{");
    }

    public record QueueStatus(long running, long waiting, long waitingVip, long waitingMain) {}
    public record QueueResponse(boolean entered, long position) {}

    private QueueResponse entered() {
        return new QueueResponse(true, 0);
    }

    private QueueResponse waiting(long pos) {
        return new QueueResponse(false, pos);
    }
}

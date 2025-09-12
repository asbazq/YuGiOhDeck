package com.card.Yugioh.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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

  private static final String LUA_EXPIRE_AND_PROMOTE_WITH_CAP = """
  -- KEYS: 1 vipRunKey, 2 mainRunKey, 3 targetRunKey, 4 vipWaitKey, 5 mainWaitKey, 6 configKey
  -- ARGV: 1 nowMs, 2 ttlMillis, 3 fallbackMaxRunning

  local vipRunKey    = KEYS[1]
  local mainRunKey   = KEYS[2]
  local targetRunKey = KEYS[3]
  local vipWaitKey   = KEYS[4]
  local mainWaitKey  = KEYS[5]
  local configKey    = KEYS[6]

  local nowMs  = tonumber(ARGV[1])
  local ttlMs  = tonumber(ARGV[2])
  local fmax   = tonumber(ARGV[3]) or 30
  local cutoff = nowMs - ttlMs

  -- 1) target에서 만료 제거
  local expired = redis.call('ZRANGEBYSCORE', targetRunKey, 0, cutoff)
  local expiredCount = #expired
  if expiredCount > 0 then
    redis.call('ZREMRANGEBYSCORE', targetRunKey, 0, cutoff)
  end

  -- 2) cap 확인 함수
  local function maxRunning()
    local m = redis.call('HGET', configKey, 'maxRunning')
    local v = tonumber(m) or fmax
    if not v or v < 1 then v = 1 end
    return v
  end

  local function totalRunning()
    return (redis.call('ZCARD', vipRunKey) or 0) + (redis.call('ZCARD', mainRunKey) or 0)
  end

  -- 3) 만료된 수만큼 승격 (target이 vip면 vip 대기 우선, main이면 main 대기 우선)
  local promoted = {}

  for i = 1, expiredCount do
    local cap = maxRunning()
    local cur = totalRunning()
    if cur >= cap then break end

    -- targetRunKey가 vip면 vipWait 우선, main이면 mainWait 우선
    local primaryWait   = (targetRunKey == vipRunKey) and vipWaitKey  or mainWaitKey
    local secondaryWait = (targetRunKey == vipRunKey) and mainWaitKey or vipWaitKey

    -- 먼저 해당 큐에서 뽑고, 없으면 다른 큐에서 폴백
    local popped = redis.call('ZPOPMIN', primaryWait, 1)
    if (not popped) or (#popped == 0) then
      popped = redis.call('ZPOPMIN', secondaryWait, 1)
    end

    if popped and #popped > 0 then
      local uid = popped[1]      -- [member, score]
      table.insert(promoted, uid)
      redis.call('ZADD', targetRunKey, nowMs, uid)
    else
      break
    end
  end

  -- 반환: [expiredCount, expired..., promotedCount, promoted...]
  local res = {}
  table.insert(res, tostring(expiredCount))
  for i = 1, expiredCount do table.insert(res, expired[i]) end
  table.insert(res, tostring(#promoted))
  for i = 1, #promoted do table.insert(res, promoted[i]) end
  return res
  """;

  @SuppressWarnings("rawtypes")
  private final RedisScript<List> expireAndPromoteScript =
      new DefaultRedisScript<>(LUA_EXPIRE_AND_PROMOTE_WITH_CAP, List.class);


  @Scheduled(fixedRate = 10_000, initialDelayString = "20000")
  public void expire() {
      long now = System.currentTimeMillis();
      // site, predict 두 그룹 각각 처리
      processOne(now, "site", "vip");
      processOne(now, "site", "main");
      broadcastStatus("site", "vip");
      broadcastStatus("site", "main");

      processOne(now, "predict", "vip");
      processOne(now, "predict", "main");
      broadcastStatus("predict", "vip");
      broadcastStatus("predict", "main");
  }

  private void processOne(long now, String group, String qid) {
    // 1) site/predict 전체 맵을 읽어와 올인원 컨피그 구성
    var siteMap    = redis.opsForHash().entries("config:{site}");
    var predictMap = redis.opsForHash().entries("config:{predict}");
    QueueConfig cfg = QueueConfig.from(siteMap, predictMap);

    // 2) 그룹별 TTL/MaxRunning 선택
    long ttl = "site".equals(group) ? cfg.sessionTtlMillisSite()
                                    : cfg.sessionTtlMillisPredict();
    if (ttl <= 0) return;

    int fallbackMax = "site".equals(group) ? cfg.maxRunningSite()
                                           : cfg.maxRunningPredict();
    if (fallbackMax < 1) fallbackMax = 1;

    // 3) 키 구성 (그룹별 running/waiting)
    String vipRun   = RUNNING_PREFIX + "{" + group + "}:vip";
    String mainRun  = RUNNING_PREFIX + "{" + group + "}:main";
    String runKey   = RUNNING_PREFIX + "{" + group + "}:" + qid;
    String vipWait  = WAITING_PREFIX + "{" + group + "}:vip";
    String mainWait = WAITING_PREFIX + "{" + group + "}:main";
    String cfgKey   = "config:{" + group + "}"; // Lua에서 HGET maxRunning 할 대상

    @SuppressWarnings("unchecked")
    List<Object> result = (List<Object>) redis.execute(
        expireAndPromoteScript,
        java.util.List.of(vipRun, mainRun, runKey, vipWait, mainWait, cfgKey),
        String.valueOf(now), String.valueOf(ttl), String.valueOf(fallbackMax)
    );

    if (result == null || result.isEmpty()) return;

    int idx = 0;
    int expiredCount = Integer.parseInt((String) result.get(idx++));
    List<String> expired = new ArrayList<>(expiredCount);
    for (int i = 0; i < expiredCount; i++) expired.add((String) result.get(idx++));

    int promotedCount = Integer.parseInt((String) result.get(idx++));
    List<String> promoted = new ArrayList<>(promotedCount);
    for (int i = 0; i < promotedCount; i++) promoted.add((String) result.get(idx++));

    // 알림
    for (String uid : expired)   notifier.sendToUser(group, uid, "{\"type\":\"TIMEOUT\"}");
    for (String uid : promoted)  notifier.sendToUser(group, uid, "{\"type\":\"ENTER\"}");
  }

  private void broadcastStatus(String group, String qid) {
      long runningCnt   = size(RUNNING_PREFIX + "{" + group + "}:" + qid);
      long vipCnt       = size(WAITING_PREFIX + "{" + group + "}:vip");
      long mainCnt      = size(WAITING_PREFIX + "{" + group + "}:main");
      long totalWaiting = vipCnt + mainCnt;

      String waitKey = WAITING_PREFIX + "{" + group + "}:" + qid;
      Set<String> waiters = redis.opsForZSet().range(waitKey, 0, -1);
      if (waiters == null) return;
      for (String uid : waiters) {
          Long rankObj = redis.opsForZSet().rank(waitKey, uid);
          long rank = rankObj == null ? 0L : rankObj + 1;
          long pos = qid.equals("main") ? vipCnt + rank : rank;

          ObjectNode msg = om.createObjectNode()
                  .put("type", "STATUS")
                  .put("group", group)
                  .put("qid", qid)
                  .put("running", runningCnt)
                  .put("waitingVip", vipCnt)
                  .put("waitingMain", mainCnt)
                  .put("waiting", totalWaiting)
                  .put("pos", pos);
          notifier.sendToUser(group, uid, msg.toString());
      }
  }

  private long size(String key) {
      Long v = redis.opsForZSet().size(key);
      return v == null ? 0L : v;
  }
}

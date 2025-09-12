package com.card.Yugioh.controller;

import com.card.Yugioh.dto.BanlistChangeNoticeDto;
import com.card.Yugioh.security.QueueConfig;
import com.card.Yugioh.service.CardService;
import com.card.Yugioh.service.ImageService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/admin/queue")
@RequiredArgsConstructor
public class QueueAdminController {

  private final RedisTemplate<String, String> redis;
  private final CardService cardService;
  private final ImageService imageService;
  private static final String BASE_API = "https://db.ygoprodeck.com/api/v7/cardinfo.php";

  /** 조회: global < group 병합 */
  @GetMapping("/all")
  public QueueConfig getGroupConfig() {
    var site    = redis.opsForHash().entries("config:{site}");
    var predict = redis.opsForHash().entries("config:{predict}");
    return QueueConfig.from(site, predict);
  }

  @PostMapping("/all")
  public QueueConfig updateGroupConfig( @RequestParam(required = false) Integer throughputSite,
                                  @RequestParam(required = false) Long sessionTtlMillisSite,
                                  @RequestParam(required = false) Integer throughputPredict,
                                  @RequestParam(required = false) Long sessionTtlMillisPredict,
                                  @RequestParam(required = false) Integer maxRunningSite,
                                  @RequestParam(required = false) Integer maxRunningPredict
  ) {
    final String SITE = "config:{site}";
    final String PRED = "config:{predict}";

    // site
    if (throughputSite != null)
      redis.opsForHash().put(SITE, "throughput", throughputSite.toString());
    if (sessionTtlMillisSite != null)
      redis.opsForHash().put(SITE, "sessionTtlMillis", sessionTtlMillisSite.toString());
    if (maxRunningSite != null)
      redis.opsForHash().put(SITE, "maxRunning", String.valueOf(Math.max(1, maxRunningSite)));

    // predict
    if (throughputPredict != null)
      redis.opsForHash().put(PRED, "throughput", throughputPredict.toString());
    if (sessionTtlMillisPredict != null)
      redis.opsForHash().put(PRED, "sessionTtlMillis", sessionTtlMillisPredict.toString());
    if (maxRunningPredict != null)
      redis.opsForHash().put(PRED, "maxRunning", String.valueOf(Math.max(1, maxRunningPredict)));

    // 최신값 반환
    var siteMap    = redis.opsForHash().entries(SITE);
    var predictMap = redis.opsForHash().entries(PRED);
    return QueueConfig.from(siteMap, predictMap);
  }

  @PostMapping("/fetchApiData")
  public ResponseEntity<?> fetchApiData(
      @RequestParam(required = false, defaultValue = "false") boolean all,
      @RequestParam(defaultValue = "500") int num,
      @RequestParam(defaultValue = "100") int offset,
      @RequestParam(defaultValue = "new") String sort
  ) throws IOException {
    try {
      int safeNum = Math.max(1, Math.min(num, 13000));
      int safeOffset = Math.max(0, offset);

      String url = all
          ? BASE_API
          : UriComponentsBuilder.fromHttpUrl(BASE_API)
          .queryParam("num", safeNum)
          .queryParam("offset", safeOffset)
          .queryParam("sort", sort)
          .toUriString();

      int processed = imageService.fetchAndSaveCardImages(url);
      return ResponseEntity.ok(
          Map.of("ok", true, "processed", processed, "requestedUrl", url)
      );
    } catch (Exception e) {
      log.error("Card fetch failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("ok", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/fetchLimitData")
  public ResponseEntity<List<BanlistChangeNoticeDto>> fetchLimitData() {
    List<BanlistChangeNoticeDto> notices = cardService.limitCrawl();
    return ResponseEntity.ok(notices);
  }

  @PostMapping("/fetchKorData")
  public void fetchKorData() {
    cardService.crawlAll();
  }
}


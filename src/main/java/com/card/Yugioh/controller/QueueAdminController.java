package com.card.Yugioh.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import com.card.Yugioh.dto.BanlistChangeNoticeDto;

import com.card.Yugioh.security.QueueConfig;
import com.card.Yugioh.service.CardService;
import com.card.Yugioh.service.ImageService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/queue")
@RequiredArgsConstructor
public class QueueAdminController {

    private final RedisTemplate<String, String> redis;
    private final CardService cardService;
    private final ImageService imageService;
    private static final String BASE_API = "https://db.ygoprodeck.com/api/v7/cardinfo.php";

    /** 현재 설정 조회 */
    @GetMapping("/{qid}")
    public QueueConfig getConfig(@PathVariable String qid) {
        Map<Object,Object> m = redis.opsForHash().entries("config:" + qid);
        Map<Object,Object> g = redis.opsForHash().entries("config:global");
        m.putAll(g);
        return QueueConfig.from(m);
    }

     /** throughput, sessionTtlMillis, maxRunning 동시 갱신 */
    @PostMapping("/{qid}")
    public void updateConfig(@PathVariable String qid,
                             @RequestParam(required = false) Integer throughput,
                             @RequestParam(required = false) Long sessionTtlMillis,
                             @RequestParam(required = false) Integer maxRunning) {
        if (throughput != null) {
            redis.opsForHash().put("config:" + qid, "throughput", throughput.toString());
        }
        if (sessionTtlMillis != null) {
            redis.opsForHash().put("config:" + qid, "sessionTtlMillis", sessionTtlMillis.toString());
        }
        if (maxRunning != null) {
            redis.opsForHash().put("config:global", "maxRunning", maxRunning.toString());
        }
    }

    @PostMapping("/fetchApiData")
    public ResponseEntity<?> fetchApiData(   
                                @RequestParam(required = false, defaultValue = "false") boolean all,
                                @RequestParam(defaultValue = "500") int num,
                                @RequestParam(defaultValue = "100") int offset,
                                @RequestParam(defaultValue = "new") String sort
                                ) throws IOException {
        try {
            // 입력값 안전 범위 보정 (원하면 상한선 조절)
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

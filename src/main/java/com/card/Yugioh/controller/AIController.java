// src/main/java/com/card/Yugioh/controller/AIController.java
package com.card.Yugioh.controller;

import com.card.Yugioh.dto.PredictCandidateDto;
import com.card.Yugioh.dto.PredictDto;
import com.card.Yugioh.dto.PredictResponseDto;
import com.card.Yugioh.dto.PredictResultDto;
import com.card.Yugioh.dto.SearchEmbedsRequestDto;
import com.card.Yugioh.dto.SearchEmbedsResponseDto;
import com.card.Yugioh.dto.UiPredictResponseDto;
import com.card.Yugioh.service.CardLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AIController {

    private final WebClient aiWebClient;
    private final CardLookupService cardLookupService;

    @PostMapping(value = "/predict", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> predict(@RequestBody SearchEmbedsRequestDto req) {
        List<List<Double>> embeds = req == null ? null : req.getEmbeds();
        if (embeds == null || embeds.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("Embeds are required."));
        }
        if (embeds.stream().anyMatch(e -> e == null || e.isEmpty())) {
            return Mono.just(ResponseEntity.badRequest().body("Embeds are required."));
        }

        return aiWebClient.post()
                .uri("/search_embeds")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(msg -> Mono.error(new RuntimeException(msg))))
                .bodyToMono(SearchEmbedsResponseDto.class)
                .flatMap(se -> {
                    List<PredictResultDto> results = se.getResults();
                    List<PredictCandidateDto> ranked = rankCandidates(results);
                    if (ranked.isEmpty()) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("No card detected."));
                    }

                    PredictDto top1 = cardLookupService.enrich(ranked.get(0));
                    if (top1 == null) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("No card detected."));
                    }

                    List<PredictDto> top4 = ranked.stream()
                            .skip(1)
                            .map(cardLookupService::enrich)
                            .filter(Objects::nonNull)
                            .limit(4)
                            .collect(Collectors.toList());

                    int count = embeds.size();
                    double elapsed = se.getElapsed() == null ? 0.0 : se.getElapsed();

                    UiPredictResponseDto body = UiPredictResponseDto.builder()
                            .detectedCount(count)
                            .top1(top1)
                            .top4(top4)
                            .elapsed(elapsed)
                            .message(null)
                            .build();

                    return Mono.just(ResponseEntity.ok(body));
                })
                .onErrorResume(ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body("AI server error: " + ex.getMessage())));
    }

    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> predictFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "vip", required = false) Boolean vip
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("File is required."));
        }

        String filename = file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        String partContentType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        partHeaders.setContentType(MediaType.parseMediaType(partContentType));
        partHeaders.setContentDispositionFormData("file", filename);
        body.add("file", new HttpEntity<>(resource, partHeaders));

        String uri = Boolean.TRUE.equals(vip) ? "/predict?vip=true" : "/predict";

        return aiWebClient.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(msg -> Mono.error(new RuntimeException(msg))))
                .bodyToMono(PredictResponseDto.class)
                .flatMap(pr -> {
                    List<PredictResultDto> results = pr == null ? null : pr.getDetections();
                    List<PredictCandidateDto> ranked = rankCandidates(results);
                    if (ranked.isEmpty()) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("No card detected."));
                    }

                    PredictDto top1 = cardLookupService.enrich(ranked.get(0));
                    if (top1 == null) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("No card detected."));
                    }

                    List<PredictDto> top4 = ranked.stream()
                            .skip(1)
                            .map(cardLookupService::enrich)
                            .filter(Objects::nonNull)
                            .limit(4)
                            .collect(Collectors.toList());

                    int count = results == null ? 0 : results.size();
                    double elapsed = pr.getElapsed() == null ? 0.0 : pr.getElapsed();

                    UiPredictResponseDto bodyResp = UiPredictResponseDto.builder()
                            .detectedCount(count)
                            .top1(top1)
                            .top4(top4)
                            .elapsed(elapsed)
                            .message(null)
                            .build();

                    return Mono.just(ResponseEntity.ok((Object) bodyResp));
                })
                .onErrorResume(ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body("AI server error: " + ex.getMessage())));
    }



    private static List<PredictCandidateDto> rankCandidates(List<PredictResultDto> dets) {
        if (dets == null || dets.isEmpty()) return List.of();

        Map<String, ScoreEntry> scores = new LinkedHashMap<>();
        for (PredictResultDto d : dets) {
            addCandidate(scores, d.getBest(), 100);
            List<PredictCandidateDto> topk = d.getTopk();
            if (topk == null || topk.isEmpty()) continue;
            int weight = topk.size();
            for (PredictCandidateDto c : topk) {
                addCandidate(scores, c, weight--);
            }
        }

        return scores.values().stream()
                .sorted(Comparator.comparingInt(ScoreEntry::score).reversed()
                        .thenComparingInt(ScoreEntry::order))
                .map(ScoreEntry::candidate)
                .collect(Collectors.toList());
    }

    private static void addCandidate(Map<String, ScoreEntry> scores, PredictCandidateDto c, int weight) {
        if (c == null) return;
        String key = candidateKey(c);
        if (key == null) return;
        ScoreEntry entry = scores.get(key);
        if (entry == null) {
            scores.put(key, new ScoreEntry(c, weight, scores.size()));
            return;
        }
        PredictCandidateDto chosen = entry.candidate().getId() == null && c.getId() != null
                ? c
                : entry.candidate();
        scores.put(key, new ScoreEntry(chosen, entry.score() + weight, entry.order()));
    }

    private static String candidateKey(PredictCandidateDto c) {
        if (c.getId() != null) return "id:" + c.getId();
        if (c.getName() != null && !c.getName().isBlank()) return "name:" + c.getName();
        return null;
    }

    private record ScoreEntry(PredictCandidateDto candidate, int score, int order) {}

}

// src/main/java/com/card/Yugioh/controller/AIController.java
package com.card.Yugioh.controller;

import com.card.Yugioh.dto.PredictCandidateDto;
import com.card.Yugioh.dto.PredictDto;
import com.card.Yugioh.dto.PredictResponseDto;
import com.card.Yugioh.dto.PredictResultDto;
import com.card.Yugioh.dto.UiPredictResponseDto;
import com.card.Yugioh.service.CardLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AIController {

    private final WebClient aiWebClient;
    private final CardLookupService cardLookupService;

    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> predict(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("이미지 파일이 없습니다."));
        }
        String filename = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "upload.jpg";
        MediaType partType = (file.getContentType() != null) ? MediaType.parseMediaType(file.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;

        ByteArrayResource resource = new ByteArrayResource(bytes(file)) {
            @Override public String getFilename() {
                return filename;
            }
        };

        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", resource)
        .filename(filename)
        .contentType(partType); // image/jpeg | image/png | image/webp

        return aiWebClient.post()
                .uri("/predict")                         // ★ FastAPI 엔드포인트에 맞춰주세요
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(mb.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class)
                                .flatMap(msg -> Mono.error(new RuntimeException(msg))))
                .bodyToMono(PredictResponseDto.class)
                .flatMap(py -> {
                    List<PredictResultDto> dets = py.getDetections();
                    int count = (dets == null) ? 0 : dets.size();

                    if (count == 0) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("카드가 감지되지 않았습니다."));
                    }
                    if (count > 1) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("이미지에 카드가 두 장 이상 감지되었습니다. 한 장만 업로드하세요."));
                    }

                    // 하나만 감지된 경우
                    PredictResultDto d = dets.get(0);

                    PredictDto top1 = cardLookupService.enrich(d.getBest());
                    List<PredictDto> top4 = (d.getTopk() == null ? List.<PredictCandidateDto>of() : d.getTopk())
                            .stream()
                            .filter(c -> !c.getId().equals(d.getBest().getId()))
                            .map(cardLookupService::enrich)
                            .limit(4)
                            .collect(Collectors.toList());

                    UiPredictResponseDto body = UiPredictResponseDto.builder()
                            .detectedCount(count)
                            .top1(top1)
                            .top4(top4)
                            .elapsed(py.getElapsed())
                            .message(null)
                            .build();

                    return Mono.just(ResponseEntity.ok(body));
                })
                .onErrorResume(ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body("AI 서버 오류: " + ex.getMessage())));
    }

    private byte[] bytes(MultipartFile f) {
        try { return f.getBytes(); }
        catch (Exception e) { throw new RuntimeException("파일 읽기 실패", e); }
    }
}

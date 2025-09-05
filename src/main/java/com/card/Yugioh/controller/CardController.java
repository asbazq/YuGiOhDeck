package com.card.Yugioh.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.dto.BanlistChangeNoticeDto;
import com.card.Yugioh.dto.BanlistChangeViewDto;
import com.card.Yugioh.dto.CardInfoDto;
import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.dto.LimitRegulationDto;
import com.card.Yugioh.model.LimitRegulationChange;
import com.card.Yugioh.model.LimitRegulationChangeBatch;
import com.card.Yugioh.repository.LimitRegulationChangeBatchRepository;
import com.card.Yugioh.repository.LimitRegulationChangeRepository;
import com.card.Yugioh.service.CardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;
    private final LimitRegulationChangeBatchRepository changeBatchRepo;
    private final LimitRegulationChangeRepository changeRepo;

    @GetMapping("/search")
    @ResponseBody
    public Page<CardMiniDto> CardSearch(@RequestParam String keyWord,
                                        @RequestParam(required = false, defaultValue = "") String frameType,
                                        Pageable pageable) {
        return cardService.search(keyWord, frameType, pageable);
    }    

    @GetMapping("/cardinfo")
    @ResponseBody
    public CardInfoDto getCardInfo(@RequestParam String cardName) {
        return cardService.getCardInfo(cardName);
    }
    
    @GetMapping("/limit")
    @ResponseBody
    public Page<LimitRegulationDto> getLimitRegulations(
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return cardService.getLimitRegulations(type, pageable);
    }

    /** 프론트가 항상 호출하는 “현재 공지” API (최신 배치 내용 반환) */
    @GetMapping("/current")
    public ResponseEntity<List<BanlistChangeNoticeDto>> currentNotice() {
        Optional<LimitRegulationChangeBatch> opt = changeBatchRepo.findTopByOrderByIdDesc();
        if (opt.isEmpty()) {
        return ResponseEntity.ok(List.of()); // 아직 배치가 없다면 빈 배열
        }
        List<LimitRegulationChange> rows = changeRepo.findByBatchOrderByIdAsc(opt.get());
        List<BanlistChangeNoticeDto> dto = rows.stream()
            .map(r -> new BanlistChangeNoticeDto(r.getCardName(), r.getOldType(), r.getNewType()))
            .toList();
        return ResponseEntity.ok(dto);
    }

    @GetMapping(value = "/notice", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BanlistChangeViewDto> getLatestNotice() {
        log.info("GET /cards/notice");
        return cardService.getLatestBanlistNotice();
    }
}

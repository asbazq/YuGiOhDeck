package com.card.Yugioh;

import java.io.IOException;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.card.Yugioh.service.CardService;
import com.card.Yugioh.service.ImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {

    private final CardService cardService;
    private final ImageService imageService;
    private final String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=100&offset=20&sort=new";

    // @PostConstruct
    public void onStartup() {
        log.info("card fetch start");
        fetchApiData();
        fetchLimitData();
    }

    // 2주마다 실행되는 스케줄러 설정 (Cron 표현식 사용)
    // 초(0초),분(0분),시간(3시), */14(14일마다), *(매월), ?(요일을 지정하지 않음)
    @Scheduled(cron = "0 0 3 * * MON")
    public void fetchApiData() {
        try {
            imageService.fetchAndSaveCardImages(apiUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
    // 초 분 시 일 월 요일
    @Scheduled(cron = "0 0 3 * * SUN")
    public void fetchLimitData() {
        cardService.limitCrawl();
    }

    // 초 분 시 일 월 요일
    @Scheduled(cron = "0 0 6 * * MON")
    public void fetchKorData() {
        cardService.crawlAll();
    }
}

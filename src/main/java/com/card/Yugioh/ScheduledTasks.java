package com.card.Yugioh;

import java.io.IOException;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.card.Yugioh.service.CardService;
import com.card.Yugioh.service.ImageService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {

    private final CardService cardService;
    private final ImageService imageService;

    @PostConstruct
    public void onStartup() {
        log.info("card fetch start");
        // fetchApiData();
        // fetchLimtData();
    }

    // 2주마다 실행되는 스케줄러 설정 (Cron 표현식 사용)
    // 초(0초),분(0분),시간(3시), */14(14일마다), *(매월), ?(요일을 지정하지 않음)
    @Scheduled(cron = "0 0 3 */14 * ?")
    public void fetchApiData() {
        try {
            imageService.fetchAndSaveCardImages();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // cardService.crawlAll();
    }
    // 초(0초),분(0분),시간(3시), 1: 매월 1일 15일 *: 매달 *: 매년
    @Scheduled(cron = "0 0 3 1,15 * *")
    public void fetchLimtData() {
        cardService.limitCrawl();
    }
}

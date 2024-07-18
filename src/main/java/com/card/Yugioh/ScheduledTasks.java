package com.card.Yugioh;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.card.Yugioh.controller.CardController;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {

    private final CardController cardController;

    @PostConstruct
    public void onStartup() {
        log.info("card fetch start");
        fetchApiData();
    }

    // 2주마다 실행되는 스케줄러 설정 (Cron 표현식 사용)
    // (초(0초),분(0분),시간(자정), */14(14일마다), *(매월), ?(요일을 지정하지 않음))
    @Scheduled(cron = "0 0 0 */14 * ?")
    public void fetchApiData() {
        cardController.fetchAndSaveCardImages();
    }
}

package com.card.Yugioh.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.service.QueueService;
import com.card.Yugioh.service.QueueService.QueueResponse;
import com.card.Yugioh.service.QueueService.QueueStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/enter")
    public QueueResponse enter(@RequestParam String group,
                           @RequestParam String qid,
                           @RequestParam String userId) {
        return queueService.enter(group, qid, userId);
    }

    @PostMapping("/leave")
    public void leave(@RequestParam String group,
                  @RequestParam String qid,
                  @RequestParam String userId) {
        queueService.leave(group, qid, userId);
    }

    @GetMapping("/position")
    public Map<String, Long> position(@RequestParam String group,
                           @RequestParam String qid,
                           @RequestParam String userId) {
        return queueService.queuePosition(group, qid, userId);
    }

    @GetMapping("/status")
    public QueueStatus status(@RequestParam String group) {
        return queueService.status(group);
    };

    @GetMapping("/isRunning")
    public Map<String, Object> isRunning(@RequestParam String group,
                                        @RequestParam String qid,
                                        @RequestParam String userId) {
        return queueService.isRunning(group, qid, userId);
    }
}
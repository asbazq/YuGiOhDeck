package com.card.Yugioh.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.service.QueueService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService svc;

    @PostMapping("/enter")
    public QueueService.QueueResponse enter(@RequestParam String qid, @RequestParam("userId") String userId) {
        return svc.enter(qid, userId);
    }

    @PostMapping("/leave")
    public void leave(@RequestParam String qid, @RequestParam("userId") String userId) {
        svc.leave(qid, userId);
    }

    @GetMapping("/status")
    public QueueService.QueueStatus status(@RequestParam String qid) { return svc.status(qid); }

    @GetMapping("/position")
    public Map<String, Long> QueuePosition(@RequestParam String qid, @RequestParam("userId") String userId) {
        return svc.QueuePosition(qid, userId);
    }

}

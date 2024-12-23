package com.card.Yugioh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.service.QueueService;

@RestController
public class UserController {
    private final QueueService queueService;

    @Autowired
    public UserController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/enter")
    public String enterSite(@RequestParam String userId) {
        queueService.addUserToQueue(userId);
        return "User " + userId + " is attempting to enter the site.";
    }

    @GetMapping("/exit")
    public String exitSite(@RequestParam String userId) {
        queueService.removeUserFromQueue(userId);
        return "User " + userId + " has exited the site.";
    }
}
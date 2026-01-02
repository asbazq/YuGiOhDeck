package com.card.Yugioh.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.service.WebAPIService;
import com.card.Yugioh.webSocket.QueueWebSocketHandler;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/api/queue")
public class WebAPIController {

    private final WebAPIService webAPIService;
    private final QueueWebSocketHandler webSocketHandler;

    public WebAPIController(WebAPIService webAPIService, QueueWebSocketHandler webSocketHandler) {
        this.webAPIService = webAPIService;
        this.webSocketHandler = webSocketHandler;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestParam("userId") String userId) {
         log.info("유저 아이디 : {}",userId);
        webAPIService.registerUser(userId);
        return ResponseEntity.ok("User " + userId + " registered in the queue.");
    }

    @GetMapping("/status")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getQueueStatus() {
        return ResponseEntity.ok(webAPIService.getQueueStatus());
    }

    @GetMapping("/position")
    public ResponseEntity<Long> getUserPosition(@RequestParam String userId) {
        Long position = webAPIService.getUserPosition(userId);
        if (position == null) {
            return ResponseEntity.badRequest().body(-1L);
        }
        return ResponseEntity.ok(position);
    }

    @GetMapping("/checkStatus")
    public ResponseEntity<Map<String, String>> checkStatus(@RequestParam String userId) {
        Map<String, String> response = new HashMap<>();
        boolean inQueue = webAPIService.isInQueue(userId);
        boolean inRunning = webAPIService.isInRunning(userId);

        if (inRunning) {
            response.put("redirectUrl", "/waitingRoomPage.html");
        } else if (inQueue) {
            response.put("redirectUrl", "/index.html");
        } else {
            response.put("redirectUrl", "/"); // 기본값
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/remove")
    public ResponseEntity<String> removeUser(@RequestParam String userId) {
        webAPIService.removeUserFromQueue(userId);
        return ResponseEntity.ok("User " + userId + " removed from the queue.");
    }

    @PostMapping("/broadcastPosition")
    public ResponseEntity<String> broadcastPosition(@RequestParam String userId) {
        Long position = webAPIService.getUserPosition(userId);
        if (position == null) {
            return ResponseEntity.badRequest().body("User not found in the queue.");
        }
    
        String message = String.format("{\"action\":\"position\", \"userId\":\"%s\", \"position\":%d}", userId, position);
        webSocketHandler.broadcastMessage(message);
        return ResponseEntity.ok("Position broadcasted for user " + userId);
    }
}

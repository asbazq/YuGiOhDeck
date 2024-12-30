package com.card.Yugioh.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.service.QueueService;

import jakarta.servlet.http.HttpSession;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class UserController {
    private final QueueService queueService;
    

    public UserController(QueueService queueService) {
        this.queueService = queueService;
    }

    // @GetMapping("/enter")
    // public RedirectView enterSite(HttpSession session) {
    //     String userId = (String) session.getAttribute("userId");
    //     if (userId == null) {
    //         userId = "user_" + Double.toString(Math.random()).substring(2, 11);
    //         session.setAttribute("userId", userId);
    //     }
    
    //     String redirectUrl = queueService.handleUserEntry(userId);
    //     return new RedirectView(redirectUrl);
    // }

    @GetMapping("/enter")
    public ResponseEntity<Map<String, String>> enterSite(HttpSession session) {
        String userId = "user_" + UUID.randomUUID().toString(); // 고유한 사용자 ID 생성
        String redirectUrl = queueService.handleUserEntry(userId);

        // 세션에는 저장하지 않고 고유 ID만 반환
        Map<String, String> response = new HashMap<>();
        response.put("userId", userId);
        response.put("redirectUrl", redirectUrl);
        return ResponseEntity.ok(response);
    }

    
    @GetMapping("/getBroadcastQueueStatus")
    public void getBroadcastQueueStatus(HttpSession session) {
        queueService.broadcastQueueStatus();
    }


    // @GetMapping("/exit")
    // public String exitSite(@RequestParam("userId") String userId) {
    //     log.info("exit userId : {}", userId);
    //     if (userId != null && !userId.isEmpty()) {
    //         queueService.removeConnectedUser(userId); // 접속 인원 관리
    //         queueService.removeUserFromQueue(userId); // 대기열에서 제거
    //         return "User " + userId + " has exited the site.";
    //     }
    //     return "Invalid userId.";
    // }
    @PostMapping("/exit")
    public ResponseEntity<String> exitSite(@RequestParam("userId") String userId, @RequestParam("refresh") boolean isRefresh) {
        log.info("Processing POST exit for userId: {}, isRefresh: {}", userId, isRefresh);
    
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid userId.");
        }
    
        if (isRefresh) {
            // 새로고침인 경우 기존 ID 제거
            queueService.removeConnectedUser(userId);
            queueService.removeUserFromQueue(userId);
            log.info("User {} removed due to refresh.", userId);
        } else {
            // 창 닫기인 경우 단순히 제거
            if (queueService.isConnectedUser(userId)) {
                queueService.removeConnectedUser(userId);
            } else {
                queueService.removeUserFromQueue(userId);
            }
        }
    
        return ResponseEntity.ok("User " + userId + " has exited.");
    }
    
}
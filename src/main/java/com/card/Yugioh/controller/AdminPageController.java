package com.card.Yugioh.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping({"/admin/queue", "/admin/queue/**"})
    public String forwardAdmin() {
        return "forward:/index.html";
    }
}
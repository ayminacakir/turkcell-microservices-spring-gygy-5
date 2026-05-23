package com.turkcell.notification_service.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class NotificationController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello notification service";
    }

}
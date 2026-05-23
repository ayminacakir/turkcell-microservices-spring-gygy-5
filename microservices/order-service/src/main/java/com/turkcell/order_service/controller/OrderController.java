package com.turkcell.order_service.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class OrderController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello order service";
    }

}
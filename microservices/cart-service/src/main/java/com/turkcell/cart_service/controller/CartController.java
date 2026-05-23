package com.turkcell.cart_service.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class CartController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello cart service";
    }

}
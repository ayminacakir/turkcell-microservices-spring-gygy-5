package com.turkcell.user_service.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class UsersController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello user service";
    }
    @GetMapping
    public String get() {
        System.out.println("UsersController çalıştı");
        return "UsersController";
    }



}

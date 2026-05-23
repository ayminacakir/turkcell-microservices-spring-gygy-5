package com.turkcell.user_service.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class UserController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello user service";
    }

}

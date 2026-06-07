package com.turkcell.user_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RequestMapping("/api/users")
@RestController
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

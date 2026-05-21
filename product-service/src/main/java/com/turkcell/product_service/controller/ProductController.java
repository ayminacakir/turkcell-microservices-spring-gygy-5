package com.turkcell.product_service.controller;

import javax.annotation.processing.Generated;

import org.springframework.web.bind.annotation.GetMapping;

public class ProductController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello product service";
    }

}

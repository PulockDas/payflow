package com.pulock.payflow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // Liveness check: if this answers, the app is up and serving HTTP.
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}

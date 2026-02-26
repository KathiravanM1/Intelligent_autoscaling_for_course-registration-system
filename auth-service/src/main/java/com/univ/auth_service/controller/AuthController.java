package com.univ.auth_service.controller;

import com.univ.auth_service.service.AuthChainedService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthChainedService chainedService;

    public AuthController(AuthChainedService chainedService) {
        this.chainedService = chainedService;
    }

    @PostMapping("/login")
    public String loginAndRegister() throws InterruptedException {
        // Simulate authentication delay
        Thread.sleep( 120);

        return chainedService.completeRegistrationFlow();
    }
}

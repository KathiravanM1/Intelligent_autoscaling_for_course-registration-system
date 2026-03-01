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

        Thread.sleep(100);

        try {
            String result = chainedService.completeRegistrationFlow();
//            System.out.println("CHAIN SUCCESS");
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR_IN_CHAIN";
        }
    }
}

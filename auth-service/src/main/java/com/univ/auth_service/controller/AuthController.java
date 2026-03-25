package com.univ.auth_service.controller;

import com.univ.auth_service.service.AuthChainedService;
import org.springframework.web.bind.annotation.*;
import java.net.InetAddress;
import java.util.Map;

@RestController
public class AuthController {

    private final AuthChainedService chainedService;

    public AuthController(AuthChainedService chainedService) {
        this.chainedService = chainedService;
    }

    @PostMapping("/login")
    public Map<String, Object> loginAndRegister() throws InterruptedException {
        Thread.sleep(100);
        String containerId = getContainerId();
        try {
            String result = chainedService.completeRegistrationFlow();
            return Map.of(
                "result", result,
                "servedBy", containerId,
                "hostname", getHostname()
            );
        } catch (Exception e) {
            return Map.of("result", "ERROR_IN_CHAIN", "servedBy", containerId, "hostname", getHostname());
        }
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("service", "auth-service", "hostname", getHostname(), "containerId", getContainerId());
    }

    private String getHostname() {
        try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "unknown"; }
    }

    private String getContainerId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return hostname.length() > 12 ? hostname.substring(0, 12) : hostname;
        } catch (Exception e) { return "unknown"; }
    }
}

package com.unv.seatservice.controller;

import org.springframework.web.bind.annotation.*;
import java.net.InetAddress;
import java.util.Map;

@RestController
public class SeatController {

    @PostMapping("/register")
    public Map<String, Object> registerSeat() throws InterruptedException {
        Thread.sleep(100);
        return Map.of(
            "result", "SEAT_REGISTERED",
            "servedBy", getContainerId(),
            "hostname", getHostname()
        );
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("service", "seat-service", "hostname", getHostname(), "containerId", getContainerId());
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

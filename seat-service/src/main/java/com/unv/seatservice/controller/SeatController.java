package com.unv.seatservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeatController {
    @PostMapping("/register")
    public String registerSeat() throws InterruptedException {
        // Simulate seat locking + DB write + contention
        Thread.sleep(100);
        return "SEAT_REGISTERED";
    }
}

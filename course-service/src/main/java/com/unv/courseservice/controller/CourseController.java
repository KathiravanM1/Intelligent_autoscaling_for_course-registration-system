package com.unv.courseservice.controller;

import org.springframework.web.bind.annotation.*;
import java.net.InetAddress;
import java.util.Map;

@RestController
public class CourseController {

    @GetMapping("/courses")
    public Map<String, Object> getCourses() throws InterruptedException {
        Thread.sleep(100);
        return Map.of(
            "result", "COURSE_LIST",
            "servedBy", getContainerId(),
            "hostname", getHostname()
        );
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("service", "course-service", "hostname", getHostname(), "containerId", getContainerId());
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

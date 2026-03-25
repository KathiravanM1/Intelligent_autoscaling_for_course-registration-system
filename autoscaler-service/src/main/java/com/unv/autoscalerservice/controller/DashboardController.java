package com.unv.autoscalerservice.controller;

import com.unv.autoscalerservice.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/api/events")
    @ResponseBody
    public Object getEvents() {
        return dashboardService.getEvents();
    }

    @GetMapping("/api/state")
    @ResponseBody
    public Object getState() {
        return dashboardService.getServiceState();
    }

    @GetMapping("/api/request-traces")
    @ResponseBody
    public Object getRequestTraces() {
        return dashboardService.getRequestTraces();
    }
}

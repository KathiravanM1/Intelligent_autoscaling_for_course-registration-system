package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.ServiceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MetricsCollectionService {
    
    @Autowired
    private PrometheusClient prometheusClient;

    public ServiceMetrics collectMetrics(String service) {
        String uri = switch (service) {
            case "auth-service" -> "/login";
            case "course-service" -> "/courses";
            case "seat-service" -> "/register";
            default -> "/";
        };

        String latencyQuery = "rate(http_server_requests_seconds_sum{job=\"" + service + "\",uri=\"" + uri + "\"}[2m]) / " +
                              "rate(http_server_requests_seconds_count{job=\"" + service + "\",uri=\"" + uri + "\"}[2m])";
        String arrivalQuery = "rate(http_server_requests_seconds_count{job=\"" + service + "\",uri=\"" + uri + "\"}[2m])";

        double latency = prometheusClient.queryValue(latencyQuery);
        double arrivalRate = prometheusClient.queryValue(arrivalQuery);

        return new ServiceMetrics(service, latency, arrivalRate, 0);
    }
}

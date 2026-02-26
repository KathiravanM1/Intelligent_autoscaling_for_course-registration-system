package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.MFDSFeatures;
import com.unv.autoscalerservice.model.ServiceMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class AutoscalingService {

    private final Map<String, Integer> new_replicas = new HashMap<>();
    private final Map<String, Double> previousArrivalRates = new HashMap<>();

    private final PrometheusClient prometheusClient;
    private final DockerScalingService dockerScalingService;
    private final MFDSDecisionEngine mFDSDecisionEngine;

    @Value("${sla.tmin}")
    private double tmin;

    @Value("${sla.tmax}")
    private double tmax;

    private double previousArrivalRate = 0.0;
    private int replicas = 1;

    public AutoscalingService(PrometheusClient prometheusClient, DockerScalingService dockerScalingService, MFDSDecisionEngine mFDSDecisionEngine) {
        this.prometheusClient = prometheusClient;
        this.dockerScalingService = dockerScalingService;
        this.mFDSDecisionEngine = mFDSDecisionEngine;

        new_replicas.put("auth-service", 1);
        new_replicas.put("course-service", 1);
        new_replicas.put("seat-service", 1);

        previousArrivalRates.put("auth-service", 0.0);
        previousArrivalRates.put("course-service", 0.0);
        previousArrivalRates.put("seat-service", 0.0);
    }

    @Scheduled(fixedDelayString = "${scaling.interval.ms}")
    public void evaluateScaling() {

        List<String> services = List.of(
                "auth-service",
                "course-service",
                "seat-service"
        );

        for (String service : services) {

            ServiceMetrics metrics = collectMetrics(service);

            MFDSFeatures features = new MFDSFeatures(
                    metrics.getLatency(),
                    metrics.getArrivalRate(),
                    metrics.getArrivalRateDelta()
            );

            ScalingAction action = mFDSDecisionEngine.decide(features);

            int currentReplicas = new_replicas.get(service);

            switch (action) {

                case SCALE_OUT:
                    new_replicas.put(service, currentReplicas + 1);
                    System.out.println("MFDS → SCALE OUT → "
                            + service + " replicas="
                            + new_replicas.get(service));
                    dockerScalingService.scaleOut(service, new_replicas.get(service));
                    break;

                case SCALE_IN:
                    if (currentReplicas > 1) {
                        new_replicas.put(service, currentReplicas - 1);
                        System.out.println("MFDS → SCALE IN → "
                                + service + " replicas="
                                + new_replicas.get(service));
                        dockerScalingService.scaleIn(service + currentReplicas);
                    }
                    break;

                default:
                    System.out.println("MFDS → NO ACTION → " + service);
            }
        }
    }

    private ServiceMetrics collectMetrics(String service) {

        double latency = prometheusClient.queryValue(
                "http_server_requests_seconds_max{application=\"" + service + "\"}"
        );

        double arrivalRate = prometheusClient.queryValue(
                "rate(http_server_requests_seconds_count{application=\"" + service + "\"}[30s])"
        );

        System.out.println("Latency = " + latency);
        System.out.println("Rate = " + arrivalRate);

        double previous = previousArrivalRates.get(service);
        double delta = arrivalRate - previous;

        previousArrivalRates.put(service, arrivalRate);

        return new ServiceMetrics(service, latency, arrivalRate, delta);
    }

}

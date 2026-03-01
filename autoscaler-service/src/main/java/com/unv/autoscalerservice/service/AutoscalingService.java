package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.MFDSFeatures;
import com.unv.autoscalerservice.model.ServiceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutoscalingService {

    private static final int MAX_REPLICAS = 5;
    private static final int MIN_REPLICAS = 1;

    // Sustained under-utilization cycles required before scale-in
    private static final int UNDER_UTILIZATION_THRESHOLD = 3;

    @Autowired
    private MFDSDecisionEngine mfdsDecisionEngine;

    @Autowired
    private FPRDecisionEngine fprDecisionEngine;

    private final Map<String, Integer> replicas = new HashMap<>();
    private final Map<String, Double> previousArrivalRates = new HashMap<>();
    private final Map<String, Long> lastScaleTime = new HashMap<>();
    private final Map<String, Integer> underUtilizationCounter = new HashMap<>();

    private final PrometheusClient prometheusClient;
    private final DockerScalingService dockerScalingService;

    public AutoscalingService(PrometheusClient prometheusClient,
                              DockerScalingService dockerScalingService) {

        this.prometheusClient = prometheusClient;
        this.dockerScalingService = dockerScalingService;

        List<String> services = List.of(
                "auth-service",
                "course-service",
                "seat-service"
        );

        for (String service : services) {
            replicas.put(service, 1);
            previousArrivalRates.put(service, 0.0);
            lastScaleTime.put(service, 0L);
            underUtilizationCounter.put(service, 0);
        }
    }

    @Scheduled(fixedDelayString = "${scaling.interval.ms}")
    public void evaluateScaling() {

        List<String> services = List.of(
                "auth-service",
                "course-service",
                "seat-service"
        );

        for (String service : services) {
            System.out.println("\n==============================");
            System.out.println("SERVICE → " + service);
            System.out.println("------------------------------");

            long now = System.currentTimeMillis();
            long lastTime = lastScaleTime.get(service);

            // Cooldown 60 sec
            if (now - lastTime < 60000) {
                System.out.println("Cooldown active → " + service);
                System.out.println("------------------------------------");
                continue;
            }

            ServiceMetrics metrics = collectMetrics(service);

            double latency = metrics.getLatency();
            double arrivalRate = metrics.getArrivalRate();
            double delta = metrics.getArrivalRateDelta();
            int currentReplicas = replicas.get(service);
            double mu = (latency > 0) ? 1.0 / latency : 0;
            double rho = (mu > 0) ? arrivalRate / (currentReplicas * mu) : 0;

            System.out.println("Latency = " + latency);
            System.out.println("ArrivalRate = " + arrivalRate);
            System.out.println("Δλ = " + delta);
            System.out.println("ρ = " + rho);

            // -------- IDLE DETECTION --------
            if (arrivalRate == 0 && currentReplicas > MIN_REPLICAS) {

                int newReplicas = currentReplicas - 1;

                System.out.println("IDLE → SCALE IN → " + service +
                        " replicas=" + newReplicas);

                replicas.put(service, newReplicas);
                dockerScalingService.scaleIn(service, newReplicas);
                lastScaleTime.put(service, now);

                System.out.println("------------------------------------");
                continue;
            }

            // -------- FPR LAYER (Predictive) --------
            if (fprDecisionEngine.shouldPreScale(
                    latency,
                    arrivalRate,
                    delta,
                    currentReplicas)) {

                int newReplicas = Math.min(MAX_REPLICAS, currentReplicas + 1);

                System.out.println("🔥 FPR PREEMPTIVE SCALE OUT → " +
                        service + " replicas=" + newReplicas);

                replicas.put(service, newReplicas);
                dockerScalingService.scaleOut(service, newReplicas);
                lastScaleTime.put(service, now);

                System.out.println("------------------------------------");
                continue;
            }

            // -------- MFDS LAYER --------
            MFDSFeatures features = new MFDSFeatures(
                    latency,
                    arrivalRate,
                    delta,
                    currentReplicas
            );

            ScalingAction action = mfdsDecisionEngine.decide(features);

//            double mu = (latency > 0) ? 1.0 / latency : 0;
//            double rho = (mu > 0) ? arrivalRate / (currentReplicas * mu) : 0;

            // -------- SCALE IN WITH HYSTERESIS --------
            if (action == ScalingAction.SCALE_IN && currentReplicas > MIN_REPLICAS) {

                underUtilizationCounter.put(service,
                        underUtilizationCounter.get(service) + 1);

                System.out.println("Underutilization count → " +
                        underUtilizationCounter.get(service));

                if (underUtilizationCounter.get(service)
                        >= UNDER_UTILIZATION_THRESHOLD) {

                    int newReplicas = currentReplicas - 1;

                    System.out.println("SUSTAINED UNDERLOAD → SCALE IN → " +
                            service + " replicas=" + newReplicas);

                    replicas.put(service, newReplicas);
                    dockerScalingService.scaleIn(service, newReplicas);
                    lastScaleTime.put(service, now);

                    underUtilizationCounter.put(service, 0);
                }

                System.out.println("------------------------------------");
                continue;
            } else {
                underUtilizationCounter.put(service, 0);
            }

            // -------- SCALE OUT WITH MSO --------
            if (action == ScalingAction.SCALE_OUT) {

                double lambda2 = arrivalRate;
                double lambda1 = arrivalRate - delta;

                if (lambda1 <= 0) {
                    System.out.println("Skipping MSO (insufficient history)");
                    System.out.println("------------------------------------");
                    continue;
                }

                double ratio = (lambda2 - lambda1) / lambda1;
                int Rm = (int) Math.floor(ratio * currentReplicas);

                if (ratio > 0 && Rm == 0) {
                    Rm = 1;
                }

                int newReplicas =
                        Math.min(MAX_REPLICAS, currentReplicas + Rm);

                if (newReplicas != currentReplicas) {

                    System.out.println("MSO SCALE OUT → " +
                            service + " replicas=" + newReplicas);

                    replicas.put(service, newReplicas);
                    dockerScalingService.scaleOut(service, newReplicas);
                    lastScaleTime.put(service, now);
                }

                System.out.println("------------------------------------");
            }
        }
    }

    private ServiceMetrics collectMetrics(String service) {

        String uri;

        switch (service) {
            case "auth-service":
                uri = "/login";
                break;
            case "course-service":
                uri = "/courses";
                break;
            case "seat-service":
                uri = "/register";
                break;
            default:
                uri = "/";
        }

        String latencyQuery =
                "rate(http_server_requests_seconds_sum{job=\"" + service + "\",uri=\"" + uri + "\"}[2m])"
                        + " / "
                        + "rate(http_server_requests_seconds_count{job=\"" + service + "\",uri=\"" + uri + "\"}[2m])";

        String arrivalQuery =
                "rate(http_server_requests_seconds_count{job=\"" + service + "\",uri=\"" + uri + "\"}[2m])";

        double latency = prometheusClient.queryValue(latencyQuery);
        double arrivalRate = prometheusClient.queryValue(arrivalQuery);

        double previous = previousArrivalRates.get(service);
        double delta = arrivalRate - previous;
        previousArrivalRates.put(service, arrivalRate);

        return new ServiceMetrics(service, latency, arrivalRate, delta);
    }
}
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

    private static final int MAX_REPLICAS = 10;
    private static final int MIN_REPLICAS = 1;
    private static final int SCALE_IN_THRESHOLD = 3;
    private static final long COOLDOWN_MS = 60000;

    @Autowired
    private MFDSDecisionEngine mfdsDecisionEngine;

    @Autowired
    private MSOEngine msoEngine;

    @Autowired
    private FPRDecisionEngine fprDecisionEngine;

    @Autowired
    private DashboardService dashboardService;

    private final Map<String, Integer> replicas = new HashMap<>();
    private final Map<String, Double> previousArrivalRates = new HashMap<>();
    private final Map<String, Long> lastScaleTime = new HashMap<>();
    private final Map<String, Integer> scaleInCounter = new HashMap<>();
    private final Map<String, Integer> mfdsScaleInCounter = new HashMap<>();

    private final PrometheusClient prometheusClient;
    private final DockerScalingService dockerScalingService;

    public AutoscalingService(PrometheusClient prometheusClient,
                              DockerScalingService dockerScalingService) {
        this.prometheusClient = prometheusClient;
        this.dockerScalingService = dockerScalingService;

        List<String> services = List.of("auth-service", "course-service", "seat-service");
        for (String service : services) {
            // Initialize with actual Docker state, not hardcoded 1
            replicas.put(service, getCurrentDockerReplicas(service));
            previousArrivalRates.put(service, 0.0);
            lastScaleTime.put(service, 0L);
            scaleInCounter.put(service, 0);
            mfdsScaleInCounter.put(service, 0);
        }
    }

    private int getCurrentDockerReplicas(String service) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                "docker", "service", "inspect", "autoscale_" + service, "--format", "{{.Spec.Mode.Replicated.Replicas}}"
            );
            Process process = builder.start();
            java.io.BufferedReader stdout = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            java.io.BufferedReader stderr = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream())
            );
            String result = stdout.readLine();
            String error = stderr.readLine();
            process.waitFor();

            if (error != null && !error.trim().isEmpty()) {
                System.out.println("⚠️ Docker stderr for " + service + ": " + error);
            }

            if (result != null && !result.trim().isEmpty()) {
                int actualReplicas = Integer.parseInt(result.trim());
                System.out.println("📋 " + service + " actual replicas from Docker: " + actualReplicas);
                return Math.min(actualReplicas, MAX_REPLICAS);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not get actual replicas for " + service + ": " + e.getMessage());
        }
        return replicas.getOrDefault(service, 1);
    }

    @Scheduled(fixedDelayString = "${scaling.interval.ms}")
    public void evaluateScaling() {
        List<String> services = List.of("auth-service", "course-service", "seat-service");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("AUTOSCALING CYCLE - " + new java.util.Date());
        System.out.println("=".repeat(80));

        for (String service : services) {
            System.out.println("\n" + "-".repeat(80));
            System.out.println("SERVICE: " + service.toUpperCase());
            System.out.println("-".repeat(80));

            long now = System.currentTimeMillis();
            if (now - lastScaleTime.get(service) < COOLDOWN_MS) {
                long remainingCooldown = (COOLDOWN_MS - (now - lastScaleTime.get(service))) / 1000;
                System.out.println("⏳ COOLDOWN ACTIVE - Remaining: " + remainingCooldown + "s");
                continue;
            }

            ServiceMetrics metrics = collectMetrics(service);
            int currentReplicas = getCurrentDockerReplicas(service); // Get actual Docker state
            replicas.put(service, currentReplicas); // Sync internal state

            double latency = metrics.getLatency();
            double lambda2 = metrics.getArrivalRate();
            double lambda1 = previousArrivalRates.get(service);
            double delta = lambda2 - lambda1;

            // Calculate queue-theoretic parameters
            double mu = (latency > 0) ? 1.0 / latency : 0;
            double rho = (mu > 0 && currentReplicas > 0) ? lambda2 / (currentReplicas * mu) : 0;

            System.out.println("\n📊 METRICS:");
            System.out.println("   Arrival Rate (λ):        " + String.format("%.4f", lambda2) + " req/s");
            System.out.println("   Previous λ:              " + String.format("%.4f", lambda1) + " req/s");
            System.out.println("   Delta (Δλ):              " + String.format("%.4f", delta) + " req/s");
            System.out.println("   Latency:                 " + String.format("%.4f", latency) + " s");
            System.out.println("   Service Rate (μ):        " + String.format("%.4f", mu) + " req/s");
            // EMERGENCY: Force scale down if above MAX_REPLICAS
            if (currentReplicas > MAX_REPLICAS) {
                int targetReplicas = MAX_REPLICAS;
                System.out.println("\n🆘 EMERGENCY SCALE DOWN");
                System.out.println("   Current Replicas:        " + currentReplicas + " (EXCEEDS LIMIT!)");
                System.out.println("   MAX_REPLICAS:            " + MAX_REPLICAS);
                System.out.println("   Target Replicas:         " + targetReplicas);
                System.out.println("   Reason:                  BOUNDARY VIOLATION - Force scale down");
                System.out.println("   Docker Command:          docker service scale autoscale_" + service + "=" + targetReplicas);
                
                replicas.put(service, targetReplicas);
                dockerScalingService.scaleIn(service, targetReplicas);
                lastScaleTime.put(service, now);
                scaleInCounter.put(service, 0);
                mfdsScaleInCounter.put(service, 0);
                
                previousArrivalRates.put(service, lambda2);
                continue;
            }

            Map<String, Object> metricsData = new HashMap<>();
            metricsData.put("latency", latency);
            metricsData.put("arrivalRate", lambda2);
            metricsData.put("replicas", currentReplicas);
            dashboardService.logEvent(service, "METRICS_COLLECTED", metricsData);

            // IDLE DETECTION WITH HYSTERESIS (λ = 0)
            if (lambda2 == 0 && currentReplicas > MIN_REPLICAS) {
                scaleInCounter.put(service, scaleInCounter.get(service) + 1);
                System.out.println("\n⚠️  IDLE DETECTED");
                System.out.println("   Idle Counter:            " + scaleInCounter.get(service) + "/" + SCALE_IN_THRESHOLD);

                if (scaleInCounter.get(service) >= SCALE_IN_THRESHOLD) {
                    int targetReplicas = currentReplicas - 1;
                    System.out.println("\n🔽 IDLE SCALE IN");
                    System.out.println("   Current Replicas:        " + currentReplicas);
                    System.out.println("   Target Replicas:         " + targetReplicas);
                    System.out.println("   Reason:                  IDLE (3 consecutive cycles)");
                    System.out.println("   Docker Command:          docker service scale autoscale_" + service + "=" + targetReplicas);

                    replicas.put(service, targetReplicas);
                    dockerScalingService.scaleIn(service, targetReplicas);
                    lastScaleTime.put(service, now);
                    scaleInCounter.put(service, 0);

                    Map<String, Object> scaleData = new HashMap<>();
                    scaleData.put("replicas", targetReplicas);
                    scaleData.put("action", "SCALE_IN");
                    scaleData.put("reason", "IDLE");
                    dashboardService.logEvent(service, "IDLE_SCALE_IN", scaleData);
                } else {
                    System.out.println("   Waiting for " + (SCALE_IN_THRESHOLD - scaleInCounter.get(service)) + " more cycle(s)");
                }
                previousArrivalRates.put(service, lambda2);
                continue;
            }

            // Reset idle counter if traffic is present
            if (lambda2 > 0 && scaleInCounter.get(service) > 0) {
                System.out.println("\n🔄 IDLE COUNTER RESET (traffic detected)");
                scaleInCounter.put(service, 0);
            }

            MFDSFeatures features = new MFDSFeatures(latency, lambda2, delta, currentReplicas);
            ScalingAction action = mfdsDecisionEngine.decide(features);

            System.out.println("\n🎯 MFDS DECISION: " + action);

            if (action == ScalingAction.SCALE_OUT && currentReplicas < MAX_REPLICAS) {
                // If traffic is decreasing, treat it as a scale-in signal instead
                if (delta < 0) {
                    int msoTarget = msoEngine.calculateScaleInReplicas(lambda1, lambda2, currentReplicas, MIN_REPLICAS);
                    mfdsScaleInCounter.put(service, mfdsScaleInCounter.get(service) + 1);
                    System.out.println("\n⚠️  TRAFFIC DECREASING — SCALE-IN CANDIDATE");
                    System.out.println("   Δλ:                      " + String.format("%.4f", delta) + " req/s");
                    System.out.println("   MSO Scale-In Target:     " + msoTarget);
                    System.out.println("   MFDS Counter:            " + mfdsScaleInCounter.get(service) + "/" + SCALE_IN_THRESHOLD);

                    if (mfdsScaleInCounter.get(service) >= SCALE_IN_THRESHOLD && msoTarget < currentReplicas) {
                        int targetReplicas = currentReplicas - 1;
                        System.out.println("\n🔽 SCALE IN (traffic drop)");
                        System.out.println("   Current Replicas:        " + currentReplicas);
                        System.out.println("   MSO Suggested:           " + msoTarget);
                        System.out.println("   Target Replicas:         " + targetReplicas + " (scale in by 1)");
                        System.out.println("   Docker Command:          docker service scale autoscale_" + service + "=" + targetReplicas);

                        replicas.put(service, targetReplicas);
                        dockerScalingService.scaleIn(service, targetReplicas);
                        lastScaleTime.put(service, now);
                        mfdsScaleInCounter.put(service, 0);

                        Map<String, Object> scaleData = new HashMap<>();
                        scaleData.put("replicas", targetReplicas);
                        scaleData.put("action", "SCALE_IN");
                        scaleData.put("reason", "TRAFFIC_DECREASE");
                        dashboardService.logEvent(service, "MSO_SCALE_IN", scaleData);
                    } else if (mfdsScaleInCounter.get(service) >= SCALE_IN_THRESHOLD) {
                        System.out.println("   Already at optimal size, resetting counter");
                        mfdsScaleInCounter.put(service, 0);
                    } else {
                        System.out.println("   Waiting for " + (SCALE_IN_THRESHOLD - mfdsScaleInCounter.get(service)) + " more cycle(s)");
                    }
                } else {
                    // MSO Calculation - Direct scaling to target
                    int msoReplicas = msoEngine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
                    int targetReplicas = Math.min(MAX_REPLICAS, msoReplicas);
                    
                    // Ensure at least 1 replica is added if MSO calculation is too conservative
                    if (targetReplicas <= currentReplicas) {
                        targetReplicas = Math.min(MAX_REPLICAS, currentReplicas + 1);
                    }
                    
                    // Final boundary check
                    if (targetReplicas > MAX_REPLICAS) {
                        targetReplicas = MAX_REPLICAS;
                    }
                    
                    int replicasToAdd = targetReplicas - currentReplicas;

                    System.out.println("\n📈 MSO CALCULATION:");
                    System.out.println("   λ1 (previous):           " + String.format("%.4f", lambda1) + " req/s");
                    System.out.println("   λ2 (current):            " + String.format("%.4f", lambda2) + " req/s");
                    if (lambda1 > 0) {
                        double ratio = (lambda2 - lambda1) / lambda1;
                        int Rm = (int) Math.ceil(ratio * currentReplicas);
                        System.out.println("   Traffic Ratio:           " + String.format("%.4f", ratio) + " (" + String.format("%.1f", ratio * 100) + "% change)");
                        System.out.println("   Rm (calculated):         " + Rm);
                    } else {
                        System.out.println("   Traffic Ratio:           N/A (λ1 = 0)");
                    }
                    System.out.println("   MSO Target:              " + msoReplicas);
                    System.out.println("   Final Target:            " + targetReplicas + " (capped at MAX_REPLICAS=" + MAX_REPLICAS + ")");

                    if (replicasToAdd > 0) {
                        System.out.println("\n🔼 SCALE OUT - DIRECT SCALING");
                        System.out.println("   Current Replicas:        " + currentReplicas);
                        System.out.println("   Target Replicas:         " + targetReplicas);
                        System.out.println("   Replicas to Add:         +" + replicasToAdd);
                        System.out.println("   Scaling Mode:            DIRECT (" + currentReplicas + " → " + targetReplicas + ")");
                        System.out.println("   Docker Command:          docker service scale autoscale_" + service + "=" + targetReplicas);
                        
                        replicas.put(service, targetReplicas);
                        dockerScalingService.scaleOut(service, targetReplicas);
                        lastScaleTime.put(service, now);
                        scaleInCounter.put(service, 0);
                        mfdsScaleInCounter.put(service, 0);

                        Map<String, Object> scaleData = new HashMap<>();
                        scaleData.put("replicas", targetReplicas);
                        scaleData.put("action", "SCALE_OUT");
                        scaleData.put("msoTarget", msoReplicas);
                        scaleData.put("replicasAdded", replicasToAdd);
                        dashboardService.logEvent(service, "MFDS_MSO_SCALE_OUT", scaleData);
                    } else {
                        System.out.println("\n🚫 SCALE OUT BLOCKED");
                        System.out.println("   Reason:                  Already at optimal size");
                        scaleInCounter.put(service, 0);
                        mfdsScaleInCounter.put(service, 0);
                    }
                }

            } else if (action == ScalingAction.SCALE_OUT && currentReplicas >= MAX_REPLICAS) {
                System.out.println("\n🚫 SCALE OUT BLOCKED");
                System.out.println("   Current Replicas:        " + currentReplicas);
                System.out.println("   MAX_REPLICAS:            " + MAX_REPLICAS);
                System.out.println("   Reason:                  Already at maximum capacity");
                scaleInCounter.put(service, 0);
                mfdsScaleInCounter.put(service, 0);

            } else if (action == ScalingAction.SCALE_IN && currentReplicas > MIN_REPLICAS) {
                mfdsScaleInCounter.put(service, mfdsScaleInCounter.get(service) + 1);
                System.out.println("\n⚠️  UNDER-UTILIZATION DETECTED");
                System.out.println("   MFDS Counter:            " + mfdsScaleInCounter.get(service) + "/" + SCALE_IN_THRESHOLD);

                if (mfdsScaleInCounter.get(service) >= SCALE_IN_THRESHOLD) {
                    int targetReplicas = currentReplicas - 1;
                    System.out.println("\n🔽 SCALE IN");
                    System.out.println("   Current Replicas:        " + currentReplicas);
                    System.out.println("   Target Replicas:         " + targetReplicas);
                    System.out.println("   Reason:                  UNDER-UTILIZATION (3 consecutive cycles)");
                    System.out.println("   Docker Command:          docker service scale autoscale_" + service + "=" + targetReplicas);
                    
                    replicas.put(service, targetReplicas);
                    dockerScalingService.scaleIn(service, targetReplicas);
                    lastScaleTime.put(service, now);
                    mfdsScaleInCounter.put(service, 0);

                    Map<String, Object> scaleData = new HashMap<>();
                    scaleData.put("replicas", targetReplicas);
                    scaleData.put("action", "SCALE_IN");
                    scaleData.put("reason", "UNDER_UTILIZATION");
                    dashboardService.logEvent(service, "MFDS_SCALE_IN", scaleData);
                } else {
                    System.out.println("   Waiting for " + (SCALE_IN_THRESHOLD - mfdsScaleInCounter.get(service)) + " more cycle(s)");
                }
            } else if (action == ScalingAction.NO_ACTION) {
                mfdsScaleInCounter.put(service, 0);
                System.out.println("\n✅ NO ACTION REQUIRED");
                System.out.println("   System is within SLA bounds");
            } else {
                mfdsScaleInCounter.put(service, 0);
            }

            previousArrivalRates.put(service, lambda2);
        }

        updateRoutingProbabilities();
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    private void updateRoutingProbabilities() {
        System.out.println("\n🌐 FPR ROUTING CALCULATION:");
        
        Map<String, Double> arrivalRates = new HashMap<>();
        for (String service : replicas.keySet()) {
            arrivalRates.put(service, previousArrivalRates.get(service));
        }

        Map<String, Double> probabilities = fprDecisionEngine.calculateRoutingProbabilities(replicas, arrivalRates);
        
        System.out.println("   Service Distribution:");
        double totalWeight = 0.0;
        for (String service : replicas.keySet()) {
            int instances = replicas.get(service);
            double lambda = arrivalRates.get(service);
            double weight = instances * lambda;
            totalWeight += weight;
            
            System.out.println("   " + service + ":");
            System.out.println("     Replicas:              " + instances);
            System.out.println("     Arrival Rate:          " + String.format("%.4f", lambda) + " req/s");
            System.out.println("     Weight:                " + String.format("%.4f", weight));
            System.out.println("     Routing Probability:   " + String.format("%.4f", probabilities.get(service)) + " (" + String.format("%.1f", probabilities.get(service) * 100) + "%)");
        }
        
        System.out.println("   Total Weight:            " + String.format("%.4f", totalWeight));
        System.out.println("   Probability Sum:         " + String.format("%.4f", probabilities.values().stream().mapToDouble(Double::doubleValue).sum()));
        
        System.out.println("\n📊 TRAFFIC DISTRIBUTION:");
        for (String service : replicas.keySet()) {
            double prob = probabilities.get(service);
            int barLength = (int) (prob * 50); // Scale to 50 chars
            String bar = "█".repeat(barLength) + "░".repeat(50 - barLength);
            System.out.println("   " + String.format("%-15s", service) + " [" + bar + "] " + String.format("%.1f", prob * 100) + "%");
        }
    }

    private ServiceMetrics collectMetrics(String service) {
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
        double delta = arrivalRate - previousArrivalRates.getOrDefault(service, 0.0);

        return new ServiceMetrics(service, latency, arrivalRate, delta);
    }
}
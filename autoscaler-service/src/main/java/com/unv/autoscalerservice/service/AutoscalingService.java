package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.MFDSFeatures;
import com.unv.autoscalerservice.model.ServiceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AutoscalingService {

    private static final int MAX_REPLICAS = 10;
    private static final int MIN_REPLICAS = 1;
    private static final int SCALE_IN_THRESHOLD = 3;
    private static final long COOLDOWN_MS = 90000;
    private static final double RHO_SCALE_IN_THRESHOLD = 0.3;
    private static final double RHO_SCALE_OUT_THRESHOLD = 0.8;

    @Value("${scaling.requests-per-replica}")
    private double requestsPerReplica;

    @Autowired private MFDSDecisionEngine mfdsDecisionEngine;
    @Autowired private MSOEngine msoEngine;
    @Autowired private FPRDecisionEngine fprDecisionEngine;
    @Autowired private DashboardService dashboardService;

    private final Map<String, Integer> replicas = new HashMap<>();
    private final Map<String, Double> previousArrivalRates = new HashMap<>();
    private final Map<String, Long> lastScaleTime = new HashMap<>();
    private final Map<String, Integer> scaleInCounter = new HashMap<>();
    private final Map<String, String> lastAction = new HashMap<>();

    private final PrometheusClient prometheusClient;
    private final DockerScalingService dockerScalingService;

    public AutoscalingService(PrometheusClient prometheusClient,
                              DockerScalingService dockerScalingService) {
        this.prometheusClient = prometheusClient;
        this.dockerScalingService = dockerScalingService;
        List<String> services = List.of("auth-service", "course-service", "seat-service");
        for (String service : services) {
            replicas.put(service, getCurrentDockerReplicas(service));
            previousArrivalRates.put(service, 0.0);
            lastScaleTime.put(service, 0L);
            scaleInCounter.put(service, 0);
            lastAction.put(service, "NO_ACTION");
        }
    }

    private int getCurrentDockerReplicas(String service) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                "docker", "service", "inspect", "autoscale_" + service,
                "--format", "{{.Spec.Mode.Replicated.Replicas}}"
            );
            Process process = builder.start();
            java.io.BufferedReader stdout = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            java.io.BufferedReader stderr = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream()));
            String result = stdout.readLine();
            String error = stderr.readLine();
            process.waitFor();
            if (error != null && !error.trim().isEmpty())
                System.out.println("Docker stderr for " + service + ": " + error);
            if (result != null && !result.trim().isEmpty()) {
                int actual = Integer.parseInt(result.trim());
            System.out.println(service + " actual replicas from Docker: " + actual);
                return Math.min(actual, MAX_REPLICAS);
            }
        } catch (Exception e) {
            System.out.println("Could not get replicas for " + service + ": " + e.getMessage());
        }
        return replicas.getOrDefault(service, 1);
    }

    private List<String> getContainerIds(String service) {
        List<String> ids = new ArrayList<>();
        try {
            ProcessBuilder builder = new ProcessBuilder(
                "docker", "service", "ps", "autoscale_" + service,
                "--filter", "desired-state=running",
                "--format", "{{.ID}}:{{.Name}}"
            );
            Process process = builder.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) ids.add(line.trim());
            }
            process.waitFor();
        } catch (Exception e) {
            System.out.println("Could not get container IDs for " + service + ": " + e.getMessage());
        }
        return ids;
    }

    @Scheduled(fixedDelayString = "${scaling.interval.ms}")
    public void evaluateScaling() {
        List<String> services = List.of("auth-service", "course-service", "seat-service");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("AUTOSCALING CYCLE - " + new java.util.Date());
        System.out.println("=".repeat(80) + "\n");

        for (String service : services) {
            System.out.println("\n" + "-".repeat(80));
            System.out.println("SERVICE: " + service.toUpperCase());
            System.out.println("-".repeat(80));

            long now = System.currentTimeMillis();
            if (now - lastScaleTime.get(service) < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - (now - lastScaleTime.get(service))) / 1000;
                System.out.println("COOLDOWN ACTIVE - Remaining: " + remaining + "s");
                pushStateToDashboard(service);
                continue;
            }

            ServiceMetrics metrics = collectMetrics(service);
            int currentReplicas = getCurrentDockerReplicas(service);
            replicas.put(service, currentReplicas);

            double latency = metrics.getLatency();
            double lambda2 = metrics.getArrivalRate();
            double lambda1 = previousArrivalRates.get(service);
            double delta = metrics.getArrivalRateDelta();
            double mu = (latency > 0) ? 1.0 / latency : 0;
            double rho = (mu > 0 && currentReplicas > 0) ? lambda2 / (currentReplicas * mu) : 0;
            double lambdaPerReplica = (currentReplicas > 0) ? lambda2 / currentReplicas : 0;

            System.out.println("\nMETRICS:");
            System.out.println("   Current Replicas:        " + currentReplicas);
            System.out.println("   Arrival Rate (lambda):   " + String.format("%.3f", lambda2) + " req/s");
            System.out.println("   Previous lambda:         " + String.format("%.3f", lambda1) + " req/s");
            System.out.println("   Delta (delta-lambda):    " + String.format("%.3f", delta) + " req/s");
            System.out.println("   Latency:                 " + String.format("%.3f", latency) + " s");
            System.out.println("   Service Rate (mu):       " + String.format("%.3f", mu) + " req/s/replica");
            System.out.println("   Utilization (rho):       " + String.format("%.3f", rho) + " (" + String.format("%.1f", rho * 100) + "%)");
            System.out.println("   lambda per Replica:      " + String.format("%.3f", lambdaPerReplica) + " req/s (capacity=" + requestsPerReplica + ")");

            if (currentReplicas > MAX_REPLICAS) {
//                System.out.println("\nEMERGENCY SCALE DOWN — exceeds MAX_REPLICAS");
                executeScaleIn(service, MAX_REPLICAS, now, "BOUNDARY_VIOLATION");
                previousArrivalRates.put(service, lambda2);
                pushStateToDashboard(service);
                continue;
            }

            dashboardService.logEvent(service, "METRICS_COLLECTED", Map.of(
                "latency", latency, "arrivalRate", lambda2,
                "replicas", currentReplicas, "rho", rho, "delta", delta
            ));

            // ── IDLE ─────────────────────────────────────────────────────────────
            if (lambda2 == 0 && currentReplicas > MIN_REPLICAS) {
                scaleInCounter.put(service, scaleInCounter.get(service) + 1);
                System.out.println("\nIDLE DETECTED");
                System.out.println("   Scale-In Counter:        " + scaleInCounter.get(service) + "/" + SCALE_IN_THRESHOLD);
                if (scaleInCounter.get(service) >= SCALE_IN_THRESHOLD) {
                    executeScaleIn(service, currentReplicas - 1, now, "IDLE");
                } else {
                    System.out.println("   Waiting for " + (SCALE_IN_THRESHOLD - scaleInCounter.get(service)) + " more cycle(s)");
                }
                previousArrivalRates.put(service, lambda2);
                pushStateToDashboardWithMetrics(service, lambda2, mu, rho, 0, 0, latency, delta);
                continue;
            }

            // ── MFDS ─────────────────────────────────────────────────────────────
            MFDSFeatures features = new MFDSFeatures(latency, lambda2, delta, currentReplicas);
            MFDSDecisionEngine.MFDSResult mfds = mfdsDecisionEngine.decide(features);

            System.out.println("\nMFDS DECISION: " + mfds.action);

            if (mfds.action == ScalingAction.SCALE_OUT) {
                // Fix 1: MFDS=SCALE_OUT always resets scale-in counter — never scale in while overloaded
                if (scaleInCounter.get(service) > 0) {
                    System.out.println("   Scale-in counter RESET — MFDS=SCALE_OUT overrides (was " + scaleInCounter.get(service) + ")");
                    scaleInCounter.put(service, 0);
                }

                if (currentReplicas >= MAX_REPLICAS) {
                    System.out.println("\nSCALE OUT BLOCKED — already at MAX_REPLICAS=" + MAX_REPLICAS);

                } else if (delta >= 0) {
                    // Traffic increasing — MSO computes optimal N via ceil(lambda/mu/0.7)
                    int msoTarget = msoEngine.calculateOptimalReplicas(lambda2, mu, currentReplicas);
                    int target    = Math.min(MAX_REPLICAS, msoTarget);

                    System.out.println("\nMSO SCALE OUT:");
                    System.out.println("   lambda=" + String.format("%.3f", lambda2) + "  mu=" + String.format("%.3f", mu));
                    System.out.println("   rho=" + String.format("%.3f", rho) + "  W=" + formatW(mfds.W));
                    System.out.println("   MSO Target: ceil(" + String.format("%.3f", lambda2)
                            + " / (" + String.format("%.3f", mu) + " x 0.7)) = " + msoTarget + "  Final: " + target);
                    System.out.println("   Docker Command: docker service scale autoscale_" + service + "=" + target);

                    replicas.put(service, target);
                    dockerScalingService.scaleOut(service, target);
                    lastScaleTime.put(service, now);
                    lastAction.put(service, "SCALE_OUT");

                    dashboardService.logEvent(service, "MFDS_MSO_SCALE_OUT", Map.of(
                        "replicas", target, "action", "SCALE_OUT",
                        "msoTarget", msoTarget, "rho", rho, "W", mfds.W
                    ));

                } else {
                    // MFDS=SCALE_OUT but delta<0 — system still overloaded, hold replicas
                    System.out.println("\nHOLDING — MFDS=SCALE_OUT but delta<0, replicas held at " + currentReplicas);
                }

            } else {
                // MFDS=SCALE_IN or MFDS=NO_ACTION — evaluate scale-in conditions
                evaluateScaleIn(service, currentReplicas, rho, mfds.W, lambdaPerReplica, delta, now);
            }

            previousArrivalRates.put(service, lambda2);
            pushStateToDashboardWithMetrics(service, lambda2, mu, rho, mfds.W, mfds.Lq, latency, delta);
        }

        updateRoutingProbabilities();
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    private static String formatW(double W) {
        if (W > 1e10) return String.format("%.3e", W) + "s";
        return String.format("%.3f", W) + "s";
    }

    private void evaluateScaleIn(String service, int currentReplicas,
                                  double rho, double W, double lambdaPerReplica, double delta, long now) {
        if (currentReplicas <= MIN_REPLICAS) {
            System.out.println("\nNO ACTION — already at MIN_REPLICAS=" + MIN_REPLICAS);
            return;
        }

        double tmin = 0.2;
        boolean wUnderUtilized = W > 0 && W < tmin;
        boolean rhoLow          = rho < RHO_SCALE_IN_THRESHOLD;
        boolean capacityLow     = lambdaPerReplica < requestsPerReplica * 0.3;
        boolean arrivalDecreasing = delta < 0;

        System.out.println("\nSCALE-IN EVALUATION:");
        System.out.println("   W < Tmin:                " + wUnderUtilized + " (W=" + formatW(W) + ")");
        System.out.println("   rho < " + RHO_SCALE_IN_THRESHOLD + ":              " + rhoLow + " (rho=" + String.format("%.3f", rho) + ")");
        System.out.println("   lambda/replica < 30% cap:" + capacityLow + " (lambda/r=" + String.format("%.3f", lambdaPerReplica) + ")");
        System.out.println("   delta-lambda < 0:        " + arrivalDecreasing + " (delta=" + String.format("%.3f", delta) + " req/s)");

        boolean anyConditionMet = (wUnderUtilized ? 1 : 0) + (rhoLow ? 1 : 0)
                                 + (capacityLow ? 1 : 0) + (arrivalDecreasing ? 1 : 0) >= 2;

        if (anyConditionMet) {
            int counter = scaleInCounter.get(service) + 1;
            scaleInCounter.put(service, counter);
            System.out.println("   Scale-In Counter:        " + counter + "/" + SCALE_IN_THRESHOLD);
            if (counter >= SCALE_IN_THRESHOLD) {
                // MSO computes safe target: ceil(lambda / (mu * 0.7)), never below MIN_REPLICAS
                double mu = (W > 0 && W < 1e10) ? 1.0 / W : (lambdaPerReplica > 0 ? lambdaPerReplica : 1.0);
                double currentLambda = lambdaPerReplica * currentReplicas;
                int safeTarget = msoEngine.calculateScaleInReplicas(currentLambda, 1.0 / (W > 0 && W < 1e10 ? W : 0.1), MIN_REPLICAS);
                safeTarget = Math.min(safeTarget, currentReplicas - 1); // always remove at least 1
                String reason = (wUnderUtilized && rhoLow) ? "UNDER_UTILIZATION" : "DECREASING_ARRIVAL_RATE";
                executeScaleIn(service, safeTarget, now, reason);
            } else {
                System.out.println("   Waiting for " + (SCALE_IN_THRESHOLD - counter) + " more cycle(s)");
            }
        } else {
            int prev = scaleInCounter.get(service);
            if (prev > 0) {
                System.out.println("   No condition met — counter RESET (was " + prev + ")");
                scaleInCounter.put(service, 0);
            } else {
                System.out.println("\nNO ACTION — system within acceptable bounds");
            }
        }
    }

    private void executeScaleIn(String service, int target, long now, String reason) {
        target = Math.max(MIN_REPLICAS, target);
        System.out.println("\nSCALE IN");
        System.out.println("   Target Replicas:         " + target);
        System.out.println("   Reason:                  " + reason);
        System.out.println("   Docker Command:          docker service scale autoscale_" + service + "=" + target);

        replicas.put(service, target);
        dockerScalingService.scaleIn(service, target);
        lastScaleTime.put(service, now);
        scaleInCounter.put(service, 0);
        lastAction.put(service, "SCALE_IN");

        dashboardService.logEvent(service, "SCALE_IN", Map.of(
            "replicas", target, "action", "SCALE_IN", "reason", reason
        ));
    }

    private void pushStateToDashboard(String service) {
        pushStateToDashboardWithMetrics(service, previousArrivalRates.getOrDefault(service, 0.0), 0, 0, 0, 0, 0, 0);
    }

    private void pushStateToDashboardWithMetrics(String service, double lambda, double mu,
                                                  double rho, double W, double Lq, double latency, double delta) {
        int currentReplicas = replicas.getOrDefault(service, 1);
        List<String> containerIds = getContainerIds(service);

        if (lambda > 0 && currentReplicas > 0) {
            double sharePerContainer = lambda / currentReplicas;
            for (String cid : containerIds) {
                dashboardService.logRequestTrace(service, cid, sharePerContainer);
            }
        }

        dashboardService.updateServiceState(
            service, currentReplicas, lambda, mu, rho, W, Lq, latency,
            containerIds, lastAction.getOrDefault(service, "NO_ACTION"), delta
        );
    }

    private void updateRoutingProbabilities() {
        System.out.println("\nFPR ROUTING CALCULATION:");
        Map<String, Double> arrivalRates = new HashMap<>();
        for (String service : replicas.keySet()) {
            arrivalRates.put(service, previousArrivalRates.get(service));
        }
        Map<String, Double> probabilities = fprDecisionEngine.calculateRoutingProbabilities(replicas, arrivalRates);
        System.out.println("  Service Distribution:");
        for (String service : replicas.keySet()) {
            double prob = probabilities.get(service);
            System.out.println("   " + service + ": replicas=" + replicas.get(service)
                + "  λ=" + String.format("%.4f", arrivalRates.get(service))
                + "  routing=" + String.format("%.1f", prob * 100) + "%");
        }
        System.out.println("\nTRAFFIC DISTRIBUTION:");
        for (String service : replicas.keySet()) {
            double prob = probabilities.get(service);
            int barLength = (int) (prob * 50);
            String bar = "█".repeat(barLength) + "░".repeat(50 - barLength);
            System.out.println("   " + String.format("%-15s", service) + " [" + bar + "] " + String.format("%.1f", prob * 100) + "%");
        }
    }

    private ServiceMetrics collectMetrics(String service) {
        String uri = switch (service) {
            case "auth-service"  -> "/login";
            case "course-service" -> "/courses";
            case "seat-service"  -> "/register";
            default -> "/";
        };

        // Fix 2: Use [2m] as primary window for stable readings across phase transitions.
        // Fall back to [1m] then [30s] only if no data exists yet (service just started).
        String[] windows = {"2m", "1m", "30s"};
        double latency     = 0.0;
        double arrivalRate = 0.0;

        for (String window : windows) {
            String latencyQuery  = "rate(http_server_requests_seconds_sum{job=\"" + service
                    + "\",uri=\"" + uri + "\"}[" + window + "]) / "
                    + "rate(http_server_requests_seconds_count{job=\"" + service
                    + "\",uri=\"" + uri + "\"}[" + window + "])";
            String arrivalQuery  = "rate(http_server_requests_seconds_count{job=\"" + service
                    + "\",uri=\"" + uri + "\"}[" + window + "])";

            latency     = prometheusClient.queryValue(latencyQuery);
            arrivalRate = prometheusClient.queryValue(arrivalQuery);

            if (latency > 0 || arrivalRate > 0) {
                System.out.println("   Metrics window used:     [" + window + "]");
                break;
            }
        }

        double delta = arrivalRate - previousArrivalRates.getOrDefault(service, 0.0);
        return new ServiceMetrics(service, latency, arrivalRate, delta);
    }
}

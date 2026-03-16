package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class FPRDecisionEngine {

    /**
     * Calculate routing probabilities for traffic distribution among containers
     * Distributes traffic evenly among containers of the same service
     */
    public Map<String, Double> calculateRoutingProbabilities(Map<String, Integer> replicaCounts, 
                                                             Map<String, Double> arrivalRates) {
        Map<String, Double> weights = new HashMap<>();
        double totalWeight = 0.0;

        // Calculate weights based on replica count and arrival rate
        for (String service : replicaCounts.keySet()) {
            int instances = replicaCounts.get(service);
            double arrivalRate = arrivalRates.getOrDefault(service, 0.0);
            
            // Weight = instances * arrival_rate (capacity * demand)
            double weight = instances * Math.max(arrivalRate, 0.1); // Minimum weight to avoid zero
            weights.put(service, weight);
            totalWeight += weight;
        }

        // Calculate probabilities
        Map<String, Double> probabilities = new HashMap<>();
        for (String service : weights.keySet()) {
            double prob = totalWeight > 0 ? weights.get(service) / totalWeight : 1.0 / weights.size();
            probabilities.put(service, prob);
        }

        return probabilities;
    }
    
    /**
     * Calculate per-container routing for a specific service
     * Distributes traffic evenly among all containers of the same service
     */
    public Map<String, Double> calculatePerContainerRouting(String serviceName, int containerCount) {
        Map<String, Double> containerRouting = new HashMap<>();
        
        if (containerCount <= 0) {
            return containerRouting;
        }
        
        double probabilityPerContainer = 1.0 / containerCount;
        
        for (int i = 1; i <= containerCount; i++) {
            String containerName = serviceName + "_" + i;
            containerRouting.put(containerName, probabilityPerContainer);
        }
        
        return containerRouting;
    }
    
    /**
     * Generate nginx upstream configuration for load balancing
     */
    public String generateNginxUpstream(String serviceName, int containerCount) {
        StringBuilder upstream = new StringBuilder();
        upstream.append("upstream ").append(serviceName.replace("-", "_")).append("_cluster {\n");
        
        for (int i = 1; i <= containerCount; i++) {
            upstream.append("    server ").append(serviceName).append(":");
            
            // Map service to port
            String port = switch (serviceName) {
                case "auth-service" -> "8080";
                case "course-service" -> "8081";
                case "seat-service" -> "8082";
                default -> "8080";
            };
            
            upstream.append(port).append(";\n");
        }
        
        upstream.append("}\n");
        return upstream.toString();
    }
}
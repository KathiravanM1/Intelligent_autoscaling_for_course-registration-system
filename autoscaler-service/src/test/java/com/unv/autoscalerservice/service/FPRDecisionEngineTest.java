package com.unv.autoscalerservice.service;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FPRDecisionEngineTest {

    @Test
    void testCalculateRoutingProbabilities() {
        FPRDecisionEngine engine = new FPRDecisionEngine();
        
        Map<String, Integer> replicaCounts = new HashMap<>();
        replicaCounts.put("auth-service", 2);
        replicaCounts.put("course-service", 3);
        replicaCounts.put("seat-service", 5);
        
        Map<String, Double> arrivalRates = new HashMap<>();
        arrivalRates.put("auth-service", 10.0);
        arrivalRates.put("course-service", 10.0);
        arrivalRates.put("seat-service", 10.0);
        
        Map<String, Double> probabilities = engine.calculateRoutingProbabilities(replicaCounts, arrivalRates);
        
        System.out.println("FPR Test 1: " + probabilities);
        
        double totalProb = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, totalProb, 0.001);
        
        assertEquals(0.2, probabilities.get("auth-service"), 0.001);
        assertEquals(0.3, probabilities.get("course-service"), 0.001);
        assertEquals(0.5, probabilities.get("seat-service"), 0.001);
    }

    @Test
    void testCalculateRoutingProbabilitiesWithDifferentRates() {
        FPRDecisionEngine engine = new FPRDecisionEngine();
        
        Map<String, Integer> replicaCounts = new HashMap<>();
        replicaCounts.put("auth-service", 2);
        replicaCounts.put("course-service", 2);
        
        Map<String, Double> arrivalRates = new HashMap<>();
        arrivalRates.put("auth-service", 50.0);
        arrivalRates.put("course-service", 100.0);
        
        Map<String, Double> probabilities = engine.calculateRoutingProbabilities(replicaCounts, arrivalRates);
        
        System.out.println("FPR Test 2: " + probabilities);
        
        double totalProb = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, totalProb, 0.001);
    }
}

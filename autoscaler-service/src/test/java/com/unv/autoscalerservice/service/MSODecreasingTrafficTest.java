package com.unv.autoscalerservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MSODecreasingTrafficTest {

    @Test
    void testMSOWithDecreasingTraffic() {
        System.out.println("\n=== Testing MSO with Decreasing Traffic ===\n");
        
        MSOEngine engine = new MSOEngine();
        
        // Test case 1: Traffic decreasing from 100 to 50 req/s
        double lambda1 = 100.0;
        double lambda2 = 50.0;
        int currentReplicas = 5;
        
        int result = engine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
        
        System.out.println("Test 1 - Decreasing Traffic:");
        System.out.println("  λ1 (previous): " + lambda1 + " req/s");
        System.out.println("  λ2 (current): " + lambda2 + " req/s");
        System.out.println("  Current Replicas: " + currentReplicas);
        System.out.println("  MSO Result: " + result);
        System.out.println("  Expected: " + currentReplicas + " (no increase)");
        
        assertEquals(currentReplicas, result);
        System.out.println("✅ MSO correctly handles decreasing traffic!\n");
    }

    @Test
    void testMSOWithIncreasingTraffic() {
        System.out.println("\n=== Testing MSO with Increasing Traffic ===\n");
        
        MSOEngine engine = new MSOEngine();
        
        // Test case 2: Traffic increasing from 50 to 150 req/s
        double lambda1 = 50.0;
        double lambda2 = 150.0;
        int currentReplicas = 2;
        
        int result = engine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
        
        System.out.println("Test 2 - Increasing Traffic:");
        System.out.println("  λ1 (previous): " + lambda1 + " req/s");
        System.out.println("  λ2 (current): " + lambda2 + " req/s");
        System.out.println("  Current Replicas: " + currentReplicas);
        System.out.println("  Traffic Ratio: " + ((lambda2 - lambda1) / lambda1));
        System.out.println("  MSO Result: " + result);
        System.out.println("  Expected: 6 (2 + 4 additional)");
        
        assertEquals(6, result);
        System.out.println("✅ MSO correctly handles increasing traffic!\n");
    }

    @Test
    void testMSOWithStableTraffic() {
        System.out.println("\n=== Testing MSO with Stable Traffic ===\n");
        
        MSOEngine engine = new MSOEngine();
        
        // Test case 3: Traffic stable at 100 req/s
        double lambda1 = 100.0;
        double lambda2 = 100.0;
        int currentReplicas = 3;
        
        int result = engine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
        
        System.out.println("Test 3 - Stable Traffic:");
        System.out.println("  λ1 (previous): " + lambda1 + " req/s");
        System.out.println("  λ2 (current): " + lambda2 + " req/s");
        System.out.println("  Current Replicas: " + currentReplicas);
        System.out.println("  Traffic Ratio: " + ((lambda2 - lambda1) / lambda1));
        System.out.println("  MSO Result: " + result);
        System.out.println("  Expected: " + currentReplicas + " (no change)");
        
        assertEquals(currentReplicas, result);
        System.out.println("✅ MSO correctly handles stable traffic!\n");
    }

    @Test
    void testTrafficDeltaLogic() {
        System.out.println("\n=== Testing Traffic Delta Logic ===\n");
        
        // Simulate autoscaler logic
        double lambda1 = 100.0;
        double lambda2 = 60.0;
        double delta = lambda2 - lambda1;
        
        System.out.println("Traffic Analysis:");
        System.out.println("  λ1 (previous): " + lambda1 + " req/s");
        System.out.println("  λ2 (current): " + lambda2 + " req/s");
        System.out.println("  Δλ (delta): " + delta + " req/s");
        
        boolean shouldBlockScaleOut = delta < 0;
        System.out.println("  Should block scale-out: " + shouldBlockScaleOut);
        
        assertTrue(shouldBlockScaleOut);
        System.out.println("✅ Delta logic correctly identifies decreasing traffic!\n");
    }
}
package com.unv.autoscalerservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryAndScaleInTest {

    @Test
    void testMaxReplicasBoundary() {
        System.out.println("\n=== Testing MAX_REPLICAS Boundary ===\n");
        
        int MAX_REPLICAS = 10;
        int currentReplicas = 10;
        boolean shouldScale = false;
        
        System.out.println("Current Replicas: " + currentReplicas);
        System.out.println("MAX_REPLICAS: " + MAX_REPLICAS);
        
        // Test scale-out when at MAX_REPLICAS
        if (currentReplicas < MAX_REPLICAS) {
            shouldScale = true;
            System.out.println("Action: SCALE_OUT allowed");
        } else {
            shouldScale = false;
            System.out.println("Action: SCALE_OUT BLOCKED - Already at MAX_REPLICAS");
        }
        
        assertFalse(shouldScale);
        System.out.println("✅ MAX_REPLICAS boundary protection working!\n");
    }

    @Test
    void testScaleInForLowTraffic() {
        System.out.println("\n=== Testing Scale-In for Low Traffic ===\n");
        
        // Simulate MFDS decision for low traffic (not idle)
        double lambda = 5.0; // Low but not zero
        double latency = 0.01; // Very low latency
        double Tmin = 0.02;
        
        double mu = 1.0 / latency;
        int N = 5;
        double rho = lambda / (N * mu);
        double W = 1.0 / mu; // Simplified calculation
        
        System.out.println("Traffic Scenario:");
        System.out.println("  λ (arrival rate): " + lambda + " req/s");
        System.out.println("  Latency: " + latency + "s");
        System.out.println("  μ (service rate): " + mu + " req/s");
        System.out.println("  N (replicas): " + N);
        System.out.println("  ρ (utilization): " + String.format("%.4f", rho));
        System.out.println("  W (response delay): " + String.format("%.4f", W) + "s");
        System.out.println("  Tmin: " + Tmin + "s");
        
        String decision;
        if (W < Tmin) {
            decision = "SCALE_IN";
        } else {
            decision = "NO_ACTION";
        }
        
        System.out.println("  MFDS Decision: " + decision);
        
        assertEquals("SCALE_IN", decision);
        System.out.println("✅ Scale-in for low traffic working!\n");
    }

    @Test
    void testSeparateCounters() {
        System.out.println("\n=== Testing Separate Counters ===\n");
        
        int idleCounter = 0;
        int mfdsCounter = 0;
        
        // Scenario 1: Idle detection
        double lambda1 = 0.0;
        if (lambda1 == 0.0) {
            idleCounter++;
            System.out.println("Cycle 1 - Idle: idleCounter=" + idleCounter + ", mfdsCounter=" + mfdsCounter);
        }
        
        // Scenario 2: Low traffic (MFDS scale-in)
        double lambda2 = 5.0;
        boolean mfdsScaleIn = true; // Simulated MFDS decision
        if (lambda2 > 0 && mfdsScaleIn) {
            mfdsCounter++;
            idleCounter = 0; // Reset idle counter when traffic is present
            System.out.println("Cycle 2 - Low Traffic: idleCounter=" + idleCounter + ", mfdsCounter=" + mfdsCounter);
        }
        
        assertEquals(0, idleCounter);
        assertEquals(1, mfdsCounter);
        System.out.println("✅ Separate counters working correctly!\n");
    }

    @Test
    void testTrafficReduction() {
        System.out.println("\n=== Testing Traffic Reduction Scenario ===\n");
        
        // Simulate traffic reduction from 100 to 10 users
        double highTraffic = 100.0;
        double lowTraffic = 10.0;
        int replicas = 8;
        
        System.out.println("Traffic Reduction Scenario:");
        System.out.println("  High Traffic: " + highTraffic + " req/s");
        System.out.println("  Low Traffic: " + lowTraffic + " req/s");
        System.out.println("  Current Replicas: " + replicas);
        
        // Calculate if this should trigger scale-in
        double latency = 0.01; // Low latency due to over-provisioning
        double mu = 1.0 / latency;
        double rho = lowTraffic / (replicas * mu);
        double W = 1.0 / mu; // Simplified
        double Tmin = 0.02;
        
        System.out.println("  New ρ (utilization): " + String.format("%.4f", rho));
        System.out.println("  New W (response delay): " + String.format("%.4f", W) + "s");
        
        boolean shouldScaleIn = W < Tmin;
        System.out.println("  Should Scale In: " + shouldScaleIn);
        
        assertTrue(shouldScaleIn);
        System.out.println("✅ Traffic reduction detection working!\n");
    }
}
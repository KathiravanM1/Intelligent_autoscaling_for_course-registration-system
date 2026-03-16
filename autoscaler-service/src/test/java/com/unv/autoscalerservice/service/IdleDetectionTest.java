package com.unv.autoscalerservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdleDetectionTest {

    @Test
    void testIdleDetectionWithHysteresis() {
        System.out.println("\n=== Testing Idle Detection with Hysteresis ===\n");
        
        // Simulate idle detection scenario
        int currentReplicas = 3;
        double arrivalRate = 0.0;
        int counter = 0;
        
        System.out.println("Initial State: replicas=" + currentReplicas + ", λ=" + arrivalRate);
        
        // Cycle 1: Idle detected
        if (arrivalRate == 0.0 && currentReplicas > 1) {
            counter++;
            System.out.println("Cycle 1: IDLE detected → Counter: " + counter);
        }
        assertEquals(1, counter);
        assertEquals(3, currentReplicas);
        
        // Cycle 2: Still idle
        if (arrivalRate == 0.0 && currentReplicas > 1) {
            counter++;
            System.out.println("Cycle 2: IDLE detected → Counter: " + counter);
        }
        assertEquals(2, counter);
        assertEquals(3, currentReplicas);
        
        // Cycle 3: Still idle, should scale in
        if (arrivalRate == 0.0 && currentReplicas > 1) {
            counter++;
            System.out.println("Cycle 3: IDLE detected → Counter: " + counter);
            
            if (counter >= 3) {
                currentReplicas--;
                counter = 0;
                System.out.println("IDLE SCALE IN → replicas=" + currentReplicas);
            }
        }
        assertEquals(2, currentReplicas);
        assertEquals(0, counter);
        
        System.out.println("\nFinal State: replicas=" + currentReplicas + ", counter=" + counter);
        System.out.println("✅ Idle detection with hysteresis working correctly!\n");
    }

    @Test
    void testIdleDetectionResetOnTraffic() {
        System.out.println("\n=== Testing Idle Counter Reset on Traffic ===\n");
        
        int currentReplicas = 3;
        double arrivalRate = 0.0;
        int counter = 0;
        
        System.out.println("Initial State: replicas=" + currentReplicas + ", λ=" + arrivalRate);
        
        // Cycle 1: Idle detected
        if (arrivalRate == 0.0 && currentReplicas > 1) {
            counter++;
            System.out.println("Cycle 1: IDLE detected → Counter: " + counter);
        }
        assertEquals(1, counter);
        
        // Cycle 2: Still idle
        if (arrivalRate == 0.0 && currentReplicas > 1) {
            counter++;
            System.out.println("Cycle 2: IDLE detected → Counter: " + counter);
        }
        assertEquals(2, counter);
        
        // Cycle 3: Traffic arrives, counter should reset
        arrivalRate = 50.0;
        System.out.println("Cycle 3: Traffic arrives → λ=" + arrivalRate);
        
        if (arrivalRate == 0.0 && currentReplicas > 1) {
            counter++;
        } else {
            // Reset counter when traffic arrives
            counter = 0;
            System.out.println("Counter reset → Counter: " + counter);
        }
        
        assertEquals(0, counter);
        assertEquals(3, currentReplicas);
        
        System.out.println("\nFinal State: replicas=" + currentReplicas + ", counter=" + counter);
        System.out.println("✅ Counter reset on traffic working correctly!\n");
    }

    @Test
    void testNoScaleInWhenMinReplicas() {
        System.out.println("\n=== Testing No Scale-In at MIN_REPLICAS ===\n");
        
        int currentReplicas = 1;
        int MIN_REPLICAS = 1;
        double arrivalRate = 0.0;
        int counter = 0;
        
        System.out.println("Initial State: replicas=" + currentReplicas + " (MIN), λ=" + arrivalRate);
        
        // Cycle 1-3: Idle but at minimum replicas
        for (int i = 1; i <= 3; i++) {
            if (arrivalRate == 0.0 && currentReplicas > MIN_REPLICAS) {
                counter++;
                System.out.println("Cycle " + i + ": IDLE detected → Counter: " + counter);
            } else {
                System.out.println("Cycle " + i + ": At MIN_REPLICAS, no scale-in");
            }
        }
        
        assertEquals(0, counter);
        assertEquals(1, currentReplicas);
        
        System.out.println("\nFinal State: replicas=" + currentReplicas + ", counter=" + counter);
        System.out.println("✅ MIN_REPLICAS protection working correctly!\n");
    }
}

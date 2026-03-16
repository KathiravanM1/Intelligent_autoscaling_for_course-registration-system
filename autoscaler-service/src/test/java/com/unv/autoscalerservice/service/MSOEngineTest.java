package com.unv.autoscalerservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MSOEngineTest {

    @Test
    void testCalculateOptimalReplicasWithTrafficIncrease() {
        MSOEngine engine = new MSOEngine();
        
        double lambda1 = 50.0;
        double lambda2 = 150.0;
        int currentReplicas = 2;
        
        int result = engine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
        
        System.out.println("MSO Test 1: λ1=" + lambda1 + " λ2=" + lambda2 + " N=" + currentReplicas + " → " + result);
        assertEquals(6, result);
    }

    @Test
    void testCalculateOptimalReplicasWithSmallIncrease() {
        MSOEngine engine = new MSOEngine();
        
        double lambda1 = 100.0;
        double lambda2 = 120.0;
        int currentReplicas = 3;
        
        int result = engine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
        
        System.out.println("MSO Test 2: λ1=" + lambda1 + " λ2=" + lambda2 + " N=" + currentReplicas + " → " + result);
        assertTrue(result >= currentReplicas);
    }

    @Test
    void testCalculateOptimalReplicasWithZeroLambda1() {
        MSOEngine engine = new MSOEngine();
        
        double lambda1 = 0.0;
        double lambda2 = 100.0;
        int currentReplicas = 2;
        
        int result = engine.calculateOptimalReplicas(lambda1, lambda2, currentReplicas);
        
        System.out.println("MSO Test 3: λ1=" + lambda1 + " λ2=" + lambda2 + " N=" + currentReplicas + " → " + result);
        assertEquals(currentReplicas, result);
    }
}

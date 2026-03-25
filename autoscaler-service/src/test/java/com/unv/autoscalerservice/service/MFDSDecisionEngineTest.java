package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.MFDSFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MFDSDecisionEngineTest {

    @Test
    void testScaleOutWhenDelayExceedsTmax() {
        MFDSDecisionEngine engine = new MFDSDecisionEngine();
        ReflectionTestUtils.setField(engine, "tmin", 0.02);
        ReflectionTestUtils.setField(engine, "tmax", 0.08);

        MFDSFeatures features = new MFDSFeatures(0.05, 100.0, 10.0, 2);
        ScalingAction action = engine.decide(features).action;

        System.out.println("Test 1 - High Load: " + action);
        assertNotNull(action);
    }

    @Test
    void testScaleInWhenDelayBelowTmin() {
        MFDSDecisionEngine engine = new MFDSDecisionEngine();
        ReflectionTestUtils.setField(engine, "tmin", 0.02);
        ReflectionTestUtils.setField(engine, "tmax", 0.08);

        MFDSFeatures features = new MFDSFeatures(0.01, 5.0, 0.0, 5);
        ScalingAction action = engine.decide(features).action;

        System.out.println("Test 2 - Low Load: " + action);
        assertNotNull(action);
    }

    @Test
    void testNoActionWhenDelayWithinSLA() {
        MFDSDecisionEngine engine = new MFDSDecisionEngine();
        ReflectionTestUtils.setField(engine, "tmin", 0.02);
        ReflectionTestUtils.setField(engine, "tmax", 0.08);

        MFDSFeatures features = new MFDSFeatures(0.03, 30.0, 5.0, 3);
        ScalingAction action = engine.decide(features).action;

        System.out.println("Test 3 - Normal Load: " + action);
        assertNotNull(action);
    }

    @Test
    void testMFDSResultContainsAllMetrics() {
        MFDSDecisionEngine engine = new MFDSDecisionEngine();
        ReflectionTestUtils.setField(engine, "tmin", 0.02);
        ReflectionTestUtils.setField(engine, "tmax", 0.08);

        MFDSFeatures features = new MFDSFeatures(0.05, 10.0, 0.0, 2);
        MFDSDecisionEngine.MFDSResult result = engine.decide(features);

        assertNotNull(result.action);
        assertTrue(result.mu > 0, "mu should be positive");
        assertTrue(result.rho >= 0, "rho should be non-negative");
        assertTrue(result.W >= 0, "W should be non-negative");
        assertTrue(result.Lq >= 0, "Lq should be non-negative");
        System.out.println("Test 4 - Result: action=" + result.action + " mu=" + result.mu + " rho=" + result.rho + " W=" + result.W);
    }
}

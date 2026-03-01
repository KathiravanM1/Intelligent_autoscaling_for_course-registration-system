package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.MFDSFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MFDSDecisionEngine {

    @Value("${sla.tmin}")
    private double tmin;

    @Value("${sla.tmax}")
    private double tmax;

    public ScalingAction decide(MFDSFeatures features) {

        double lambda = features.getArrivalRate();
        double latency = features.getLatency();
        int replicas = features.getCurrentReplicas();

        if (latency <= 0 || replicas <= 0) {
            return ScalingAction.NO_ACTION;
        }

        double mu = 1.0 / latency;
        double rho = lambda / (replicas * mu);

        System.out.println("MFDS → ρ=" + rho);

        // SCALE OUT
        if (rho > 0.75) {
            return ScalingAction.SCALE_OUT;
        }

        // SCALE IN (pure utilization based)
        if (rho < 0.50) {
            return ScalingAction.SCALE_IN;
        }

        return ScalingAction.NO_ACTION;
    }
}

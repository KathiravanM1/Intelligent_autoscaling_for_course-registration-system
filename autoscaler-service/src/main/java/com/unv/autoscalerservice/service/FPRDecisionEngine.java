package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;

@Service
public class FPRDecisionEngine {

    private static final double PREDICTION_THRESHOLD = 0.8;

    public boolean shouldPreScale(double latency,
                                  double arrivalRate,
                                  double delta,
                                  int replicas) {

        if (latency <= 0 || replicas <= 0) {
            return false;
        }

        double predictedLambda = arrivalRate + delta;
        double mu = 1.0 / latency;

        double predictedRho = predictedLambda / (replicas * mu);

        System.out.println("FPR → predicted λ=" + predictedLambda +
                " predicted ρ=" + predictedRho);

        return predictedRho > PREDICTION_THRESHOLD;
    }
}
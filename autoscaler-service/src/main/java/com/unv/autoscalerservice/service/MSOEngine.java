package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;

@Service
public class MSOEngine {

    private static final double RHO_TARGET = 0.7; // keep utilization at 70% after scale-out

    /**
     * MSO: compute minimum N such that rho = lambda / (N * mu) <= RHO_TARGET.
     * Formula: N_optimal = ceil(lambda / (mu * RHO_TARGET))
     * Always returns at least currentReplicas + 1 to guarantee progress.
     */
    public int calculateOptimalReplicas(double lambda, double mu, int currentReplicas) {
        if (mu <= 0 || lambda <= 0) return currentReplicas;
        int optimal = (int) Math.ceil(lambda / (mu * RHO_TARGET));
        return Math.max(optimal, currentReplicas + 1);
    }

    /**
     * Scale-in: compute minimum N such that rho = lambda / (N * mu) <= RHO_TARGET.
     * Same formula — used to jump directly to the right replica count instead of -1 each time.
     */
    public int calculateScaleInReplicas(double lambda, double mu, int minReplicas) {
        if (mu <= 0 || lambda <= 0) return minReplicas;
        int optimal = (int) Math.ceil(lambda / (mu * RHO_TARGET));
        return Math.max(optimal, minReplicas);
    }
}

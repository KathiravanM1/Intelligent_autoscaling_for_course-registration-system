package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;

@Service
public class MSOEngine {

    /**
     * Calculate optimal replicas using MSO algorithm
     * Key requirement: If current replicas = 4 and MSO calculates 2, 
     * the result should be 6 (4 + 2), not 2
     */
    public int calculateOptimalReplicas(double lambda1, double lambda2, int currentReplicas) {
        if (lambda1 <= 0 || lambda2 <= lambda1) {
            return currentReplicas; // No scaling needed
        }
        
        // Calculate traffic increase ratio
        double ratio = (lambda2 - lambda1) / lambda1;
        
        // MSO calculation: additional replicas needed
        int additionalReplicas = (int) Math.ceil(ratio * currentReplicas);
        
        // Return current + additional (not just additional)
        return currentReplicas + additionalReplicas;
    }
    
    /**
     * Calculate scale-in decision based on traffic decrease
     */
    public int calculateScaleInReplicas(double lambda1, double lambda2, int currentReplicas, int minReplicas) {
        if (lambda1 <= 0 || lambda2 >= lambda1 || currentReplicas <= minReplicas) {
            return currentReplicas; // No scale-in needed
        }
        
        // Calculate traffic decrease ratio
        double decreaseRatio = (lambda1 - lambda2) / lambda1;
        
        // Calculate replicas to remove (conservative approach)
        int replicasToRemove = Math.max(1, (int) Math.floor(decreaseRatio * currentReplicas));
        
        // Ensure we don't go below minimum
        return Math.max(minReplicas, currentReplicas - replicasToRemove);
    }
}

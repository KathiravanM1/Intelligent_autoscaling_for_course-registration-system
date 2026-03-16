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
        int N = features.getCurrentReplicas();

        System.out.println("\n🔍 MFDS QUEUE-THEORETIC ANALYSIS:");
        System.out.println("   SLA Bounds:              Tmin=" + String.format("%.4f", tmin) + "s, Tmax=" + String.format("%.4f", tmax) + "s");

        if (latency <= 0 || N <= 0 || lambda <= 0) {
            System.out.println("   Status:                  INVALID METRICS (λ=" + lambda + ", latency=" + latency + ", N=" + N + ")");
            return ScalingAction.NO_ACTION;
        }

        double mu = 1.0 / latency;
        double rho = lambda / (N * mu);

        System.out.println("   Queue Model:             M/M/" + N);
        System.out.println("   Arrival Rate (λ):        " + String.format("%.4f", lambda) + " req/s");
        System.out.println("   Service Rate (μ):        " + String.format("%.4f", mu) + " req/s");
        System.out.println("   Utilization (ρ):         " + String.format("%.4f", rho) + " (" + String.format("%.1f", rho * 100) + "%)");

        if (rho >= 1.0) {
            System.out.println("   Status:                  SYSTEM OVERLOADED (ρ ≥ 1.0)");
            System.out.println("   Decision:                SCALE_OUT (immediate)");
            return ScalingAction.SCALE_OUT;
        }

        double p0 = calculateP0(lambda, mu, N, rho);
        double Lq = calculateLq(lambda, mu, N, rho, p0);
        double W = (Lq / lambda) + (1.0 / mu);

        System.out.println("   Steady-state Prob (p0):  " + String.format("%.6f", p0));
        System.out.println("   Queue Length (Lq):       " + String.format("%.4f", Lq) + " requests");
        System.out.println("   Response Delay (W):      " + String.format("%.4f", W) + "s");
        
        String status;
        ScalingAction decision;
        
        if (W > tmax) {
            status = "SLA VIOLATION (W > Tmax)";
            decision = ScalingAction.SCALE_OUT;
        } else if (W < tmin) {
            status = "UNDER-UTILIZED (W < Tmin)";
            decision = ScalingAction.SCALE_IN;
        } else {
            status = "WITHIN SLA BOUNDS";
            decision = ScalingAction.NO_ACTION;
        }
        
        System.out.println("   Status:                  " + status);
        System.out.println("   Decision:                " + decision);
        
        return decision;
    }

    private double calculateP0(double lambda, double mu, int N, double rho) {
        double sum = 0.0;
        double lambdaOverMu = lambda / mu;
        
        for (int n = 0; n < N; n++) {
            sum += Math.pow(lambdaOverMu, n) / factorial(n);
        }
        
        double lastTerm = Math.pow(lambdaOverMu, N) / (factorial(N) * (1 - rho));
        return 1.0 / (sum + lastTerm);
    }

    private double calculateLq(double lambda, double mu, int N, double rho, double p0) {
        double lambdaOverMu = lambda / mu;
        return p0 * Math.pow(lambdaOverMu, N) * rho / (factorial(N) * Math.pow(1 - rho, 2));
    }

    private long factorial(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
}

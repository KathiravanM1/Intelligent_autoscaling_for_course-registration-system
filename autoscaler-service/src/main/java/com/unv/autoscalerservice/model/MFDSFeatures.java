package com.unv.autoscalerservice.model;

public class MFDSFeatures {

    private final double latency;
    private final double arrivalRate;
    private final double arrivalRateDelta;
    private final int currentReplicas;

    public MFDSFeatures(double latency,
                        double arrivalRate,
                        double arrivalRateDelta,
                        int currentReplicas) {

        this.latency = latency;
        this.arrivalRate = arrivalRate;
        this.arrivalRateDelta = arrivalRateDelta;
        this.currentReplicas = currentReplicas;
    }

    public double getLatency() {
        return latency;
    }

    public double getArrivalRate() {
        return arrivalRate;
    }

    public double getArrivalRateDelta() {
        return arrivalRateDelta;
    }

    public int getCurrentReplicas() {
        return currentReplicas;
    }
}
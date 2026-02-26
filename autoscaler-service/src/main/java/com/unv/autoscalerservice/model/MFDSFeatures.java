package com.unv.autoscalerservice.model;

public class MFDSFeatures {
    private final double latency;
    private final double arrivalRate;
    private final double arrivalRateDelta;

    public MFDSFeatures(double latency, double arrivalRate, double arrivalRateDelta) {
        this.latency = latency;
        this.arrivalRate = arrivalRate;
        this.arrivalRateDelta = arrivalRateDelta;
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
}

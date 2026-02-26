package com.unv.autoscalerservice.model;

public class ServiceMetrics {

    private final String serviceName;
    private final double latency;
    private final double arrivalRate;
    private final double arrivalRateDelta;

    public ServiceMetrics(String serviceName,
                          double latency,
                          double arrivalRate,
                          double arrivalRateDelta) {
        this.serviceName = serviceName;
        this.latency = latency;
        this.arrivalRate = arrivalRate;
        this.arrivalRateDelta = arrivalRateDelta;
    }

    public String getServiceName() {
        return serviceName;
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

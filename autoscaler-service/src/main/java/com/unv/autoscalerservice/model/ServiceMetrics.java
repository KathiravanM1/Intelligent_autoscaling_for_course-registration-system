package com.unv.autoscalerservice.model;

public class ServiceMetrics {

    private final String serviceName;
    private final double latency;
    private final double arrivalRate;
    private final double arrivalRateDelta;
    
    // Extended metrics
    private double errorRate;
    private double cpuUsage;
    private double memoryUsage;
    private double throughput;
    private double responseTime95;
    private double responseTime99;
    private double healthScore;

    public ServiceMetrics(String serviceName,
                          double latency,
                          double arrivalRate,
                          double arrivalRateDelta) {
        this.serviceName = serviceName;
        this.latency = latency;
        this.arrivalRate = arrivalRate;
        this.arrivalRateDelta = arrivalRateDelta;
        
        // Initialize extended metrics
        this.errorRate = 0.0;
        this.cpuUsage = 0.0;
        this.memoryUsage = 0.0;
        this.throughput = 0.0;
        this.responseTime95 = 0.0;
        this.responseTime99 = 0.0;
        this.healthScore = 1.0;
    }

    // Getters for original metrics
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
    
    // Getters and setters for extended metrics
    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getThroughput() {
        return throughput;
    }

    public void setThroughput(double throughput) {
        this.throughput = throughput;
    }

    public double getResponseTime95() {
        return responseTime95;
    }

    public void setResponseTime95(double responseTime95) {
        this.responseTime95 = responseTime95;
    }

    public double getResponseTime99() {
        return responseTime99;
    }

    public void setResponseTime99(double responseTime99) {
        this.responseTime99 = responseTime99;
    }

    public double getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(double healthScore) {
        this.healthScore = healthScore;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ServiceMetrics{service='%s', latency=%.4f, arrivalRate=%.4f, delta=%.4f, " +
            "errorRate=%.4f, cpuUsage=%.4f, memoryUsage=%.4f, healthScore=%.4f}",
            serviceName, latency, arrivalRate, arrivalRateDelta, 
            errorRate, cpuUsage, memoryUsage, healthScore
        );
    }
}
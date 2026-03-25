package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardService {

    private final Deque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> requestTraces = new ConcurrentLinkedDeque<>();
    private final Map<String, Map<String, Object>> serviceState = new ConcurrentHashMap<>();

    private static final int MAX_EVENTS = 200;
    private static final int MAX_TRACES = 50;

    public void logEvent(String service, String step, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", System.currentTimeMillis());
        event.put("service", service);
        event.put("step", step);
        event.put("data", new HashMap<>(data));
        events.addLast(event);
        while (events.size() > MAX_EVENTS) events.pollFirst();
    }

    public void updateServiceState(String service, int replicas, double lambda, double mu,
                                    double rho, double W, double Lq, double latency,
                                    List<String> containerIds, String lastAction, double deltaLambda) {
        Map<String, Object> state = new HashMap<>();
        state.put("replicas", replicas);
        state.put("lambda", lambda);
        state.put("deltaLambda", deltaLambda);
        state.put("mu", mu);
        state.put("rho", rho);
        state.put("W", W);
        state.put("Lq", Lq);
        state.put("latency", latency);
        state.put("containerIds", containerIds);
        state.put("lastAction", lastAction);
        state.put("updatedAt", System.currentTimeMillis());
        serviceState.put(service, state);
    }

    public void logRequestTrace(String service, String containerId, double lambdaShare) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("timestamp", System.currentTimeMillis());
        trace.put("service", service);
        trace.put("containerId", containerId);
        trace.put("lambdaShare", lambdaShare);
        requestTraces.addLast(trace);
        while (requestTraces.size() > MAX_TRACES) requestTraces.pollFirst();
    }

    public List<Map<String, Object>> getEvents() {
        return new ArrayList<>(events);
    }

    public List<Map<String, Object>> getRequestTraces() {
        return new ArrayList<>(requestTraces);
    }

    public Map<String, Map<String, Object>> getServiceState() {
        return new HashMap<>(serviceState);
    }
}

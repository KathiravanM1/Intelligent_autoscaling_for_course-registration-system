package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DashboardService {

    private final Queue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
    private static final int MAX_EVENTS = 100;

    public void logEvent(String service, String step, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", System.currentTimeMillis());
        event.put("service", service);
        event.put("step", step);
        event.put("data", data);
        
        events.add(event);
        if (events.size() > MAX_EVENTS) {
            events.poll();
        }
    }

    public List<Map<String, Object>> getEvents() {
        return new ArrayList<>(events);
    }
}

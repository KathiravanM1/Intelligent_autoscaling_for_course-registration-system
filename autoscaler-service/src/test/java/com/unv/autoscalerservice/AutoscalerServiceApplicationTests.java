package com.unv.autoscalerservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "scaling.requests-per-replica=5.0",
    "scaling.interval.ms=30000",
    "sla.tmin=0.02",
    "sla.tmax=0.08",
    "prometheus.base-url=http://localhost:9090"
})
class AutoscalerServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

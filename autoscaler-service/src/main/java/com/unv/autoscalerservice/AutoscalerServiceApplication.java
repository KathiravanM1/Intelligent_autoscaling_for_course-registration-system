package com.unv.autoscalerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutoscalerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoscalerServiceApplication.class, args);
    }
}

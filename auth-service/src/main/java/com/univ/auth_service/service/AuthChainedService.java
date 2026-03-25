package com.univ.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
public class AuthChainedService {


    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${course.service.url}")
    private String courseServiceUrl;

    @Value("${seat.service.url}")
    private String seatServiceUrl;

    public String completeRegistrationFlow() throws InterruptedException {
        // Step 1: Authenticate (already done by controller delay)

        // Step 2: Fetch courses
        Thread.sleep(200);
        restTemplate.getForObject(
                courseServiceUrl + "/courses",
                String.class
        );

        // Step 3: Register seat
        Thread.sleep(200);
        restTemplate.postForObject(
                seatServiceUrl + "/register",
                null,
                String.class
        );

        return "REGISTRATION_COMPLETE";
    }
}

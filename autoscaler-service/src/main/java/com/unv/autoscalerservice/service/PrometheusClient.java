package com.unv.autoscalerservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;

@Service
public class PrometheusClient {

    @Value("${prometheus.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public double queryValue(String promQuery) {
        try {
            URI uri = new URI(
                    baseUrl + "/api/v1/query?query=" +
                            java.net.URLEncoder.encode(promQuery, java.nio.charset.StandardCharsets.UTF_8)
            );

//            System.out.println("FINAL URI = " + uri);

            String response = restTemplate.getForObject(uri, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("data").path("result");

            if (result.isEmpty()) {
                return 0.0;
            }

            String value = result.get(0).path("value").get(1).asText();

            if (value.equalsIgnoreCase("NaN") || value.equalsIgnoreCase("Inf") || value.equalsIgnoreCase("-Inf")) {
                return 0.0;
            }

            return Double.parseDouble(value);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Prometheus query failed", e);
        }
    }
}

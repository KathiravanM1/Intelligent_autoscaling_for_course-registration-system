package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;

@Service
public class DockerScalingService {

    public void scaleOut(String serviceName, int replicaCount) {
        scale(serviceName, replicaCount);
    }

    public void scaleIn(String serviceName, int replicaCount) {
        scale(serviceName, replicaCount);
    }

    private void scale(String serviceName, int replicaCount) {
        try {

            // Stack name is "autoscale"
            String fullServiceName = "autoscale_" + serviceName;

            ProcessBuilder builder = new ProcessBuilder(
                    "docker",
                    "service",
                    "scale",
                    fullServiceName + "=" + replicaCount
            );

            builder.inheritIO();
            Process process = builder.start();
            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
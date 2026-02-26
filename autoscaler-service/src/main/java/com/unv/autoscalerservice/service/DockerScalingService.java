package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DockerScalingService {

    public void scaleOut(String serviceName, int replicaId) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "docker",
                    "run",
                    "-d",
                    "--network",
                    "course-network",
                    "--name",
                    serviceName + replicaId,
                    serviceName
            );

            builder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void scaleIn(String containerName) {
        try {
            new ProcessBuilder("docker", "stop", containerName).start();
            new ProcessBuilder("docker", "rm", containerName).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package com.unv.autoscalerservice.service;

import org.springframework.stereotype.Service;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

@Service
public class NginxConfigService {
    
    private static final String NGINX_CONFIG_PATH = "/etc/nginx/nginx.conf";
    private static final String LOCAL_NGINX_CONFIG_PATH = "./nginx.conf";
    
    /**
     * Generate and update nginx configuration for dynamic load balancing
     */
    public void updateNginxConfig(Map<String, Integer> replicaCounts) {
        StringBuilder config = new StringBuilder();
        
        // Events block
        config.append("events {}\n\n");
        
        // HTTP block start
        config.append("http {\n\n");
        
        // Generate upstream blocks for each service
        for (Map.Entry<String, Integer> entry : replicaCounts.entrySet()) {
            String serviceName = entry.getKey();
            int replicas = entry.getValue();
            
            config.append(generateUpstreamBlock(serviceName, replicas));
            config.append("\n");
        }
        
        // Generate server block with location routing
        config.append(generateServerBlock());
        
        // HTTP block end
        config.append("}\n");
        
        // Write configuration to file
        writeConfigToFile(config.toString());
        
        // Reload nginx
        reloadNginx();
    }
    
    private String generateUpstreamBlock(String serviceName, int replicas) {
        StringBuilder upstream = new StringBuilder();
        String upstreamName = serviceName.replace("-", "_") + "_cluster";
        
        upstream.append("    upstream ").append(upstreamName).append(" {\n");
        
        // Add server entries for each replica
        for (int i = 1; i <= replicas; i++) {
            String port = getServicePort(serviceName);
            upstream.append("        server ").append(serviceName).append(":").append(port).append(";\n");
        }
        
        upstream.append("    }\n");
        return upstream.toString();
    }
    
    private String generateServerBlock() {
        StringBuilder server = new StringBuilder();
        
        server.append("    server {\n");
        server.append("        listen 80;\n\n");
        
        // Auth service routes
        server.append("        location /login {\n");
        server.append("            proxy_pass http://auth_service_cluster;\n");
        server.append("            proxy_set_header Host $host;\n");
        server.append("            proxy_set_header X-Real-IP $remote_addr;\n");
        server.append("            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        server.append("            proxy_set_header X-Forwarded-Proto $scheme;\n");
        server.append("        }\n\n");
        
        // Course service routes
        server.append("        location /courses {\n");
        server.append("            proxy_pass http://course_service_cluster;\n");
        server.append("            proxy_set_header Host $host;\n");
        server.append("            proxy_set_header X-Real-IP $remote_addr;\n");
        server.append("            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        server.append("            proxy_set_header X-Forwarded-Proto $scheme;\n");
        server.append("        }\n\n");
        
        // Seat service routes
        server.append("        location /register {\n");
        server.append("            proxy_pass http://seat_service_cluster;\n");
        server.append("            proxy_set_header Host $host;\n");
        server.append("            proxy_set_header X-Real-IP $remote_addr;\n");
        server.append("            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        server.append("            proxy_set_header X-Forwarded-Proto $scheme;\n");
        server.append("        }\n\n");
        
        // Default route to auth service
        server.append("        location / {\n");
        server.append("            proxy_pass http://auth_service_cluster;\n");
        server.append("            proxy_set_header Host $host;\n");
        server.append("            proxy_set_header X-Real-IP $remote_addr;\n");
        server.append("            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        server.append("            proxy_set_header X-Forwarded-Proto $scheme;\n");
        server.append("        }\n");
        
        server.append("    }\n");
        return server.toString();
    }
    
    private String getServicePort(String serviceName) {
        return switch (serviceName) {
            case "auth-service" -> "8080";
            case "course-service" -> "8081";
            case "seat-service" -> "8082";
            default -> "8080";
        };
    }
    
    private void writeConfigToFile(String config) {
        try {
            // Write to local file first
            try (FileWriter writer = new FileWriter(LOCAL_NGINX_CONFIG_PATH)) {
                writer.write(config);
            }
            
            System.out.println("🔧 Nginx configuration updated successfully");
            
        } catch (IOException e) {
            System.err.println("❌ Failed to write nginx configuration: " + e.getMessage());
        }
    }
    
    private void reloadNginx() {
        try {
            // Copy config to nginx container and reload
            ProcessBuilder copyBuilder = new ProcessBuilder(
                "docker", "cp", LOCAL_NGINX_CONFIG_PATH, "autoscale_gateway_1:/etc/nginx/nginx.conf"
            );
            Process copyProcess = copyBuilder.start();
            copyProcess.waitFor();
            
            // Reload nginx
            ProcessBuilder reloadBuilder = new ProcessBuilder(
                "docker", "exec", "autoscale_gateway_1", "nginx", "-s", "reload"
            );
            Process reloadProcess = reloadBuilder.start();
            reloadProcess.waitFor();
            
            System.out.println("🔄 Nginx configuration reloaded successfully");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to reload nginx: " + e.getMessage());
        }
    }
}
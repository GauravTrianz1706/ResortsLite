package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * AWS Systems Manager Parameter Store configuration for retrieving environment-specific URLs and ports.
 * This replaces hard-coded environment URLs and ports with cloud-native configuration management.
 * 
 * FIXED cr-java-0077: Added server port retrieval from AWS Parameter Store
 */
@Configuration
public class AwsParameterStoreConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.parameterstore.inventory.url.name:/resorts-lite/inventory-service-url}")
    private String inventoryUrlParameterName;

    @Value("${aws.parameterstore.server.port.name:/resorts-lite/server-port}")
    private String serverPortParameterName;

    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public EnvironmentUrls environmentUrls(SsmClient ssmClient) {
        EnvironmentUrls urls = new EnvironmentUrls();
        
        try {
            // Retrieve inventory service URL from Parameter Store
            String inventoryUrl = getParameter(ssmClient, inventoryUrlParameterName);
            urls.setInventoryServiceUrl(inventoryUrl);
        } catch (Exception e) {
            // Fallback to default values for local development
            System.err.println("Warning: Unable to retrieve parameters from AWS Parameter Store: " + e.getMessage());
            System.err.println("Using default URLs for local development");
            urls.setInventoryServiceUrl("http://localhost:8081/rooms/available");
        }
        
        return urls;
    }

    /**
     * FIXED cr-java-0077: Added bean to retrieve server port from AWS Parameter Store
     * This enables dynamic port assignment in cloud environments (ECS, EKS, Elastic Beanstalk)
     * 
     * @param ssmClient AWS Systems Manager client
     * @return ServerPortConfig containing the dynamically retrieved port
     */
    @Bean
    public ServerPortConfig serverPortConfig(SsmClient ssmClient) {
        ServerPortConfig config = new ServerPortConfig();
        
        try {
            // Retrieve server port from Parameter Store
            String portValue = getParameter(ssmClient, serverPortParameterName);
            config.setPort(Integer.parseInt(portValue));
        } catch (Exception e) {
            // Fallback to default port for local development
            System.err.println("Warning: Unable to retrieve server port from AWS Parameter Store: " + e.getMessage());
            System.err.println("Using default port 8080 for local development");
            config.setPort(8080);
        }
        
        return config;
    }

    private String getParameter(SsmClient ssmClient, String parameterName) {
        GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(true)
                .build();

        GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
        return parameterResponse.parameter().value();
    }

    /**
     * POJO to hold environment-specific URLs retrieved from AWS Parameter Store
     */
    public static class EnvironmentUrls {
        private String inventoryServiceUrl;

        public String getInventoryServiceUrl() {
            return inventoryServiceUrl;
        }

        public void setInventoryServiceUrl(String inventoryServiceUrl) {
            this.inventoryServiceUrl = inventoryServiceUrl;
        }
    }

    /**
     * POJO to hold server port configuration retrieved from AWS Parameter Store
     * FIXED cr-java-0077: Enables dynamic port assignment in cloud environments
     */
    public static class ServerPortConfig {
        private int port;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}

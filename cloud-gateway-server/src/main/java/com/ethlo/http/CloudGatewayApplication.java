package com.ethlo.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableConfigurationProperties
@EnableScheduling
@SpringBootApplication(exclude = org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class)
public class CloudGatewayApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(CloudGatewayApplication.class, args);
    }
}

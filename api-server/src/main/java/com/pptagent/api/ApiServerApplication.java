package com.pptagent.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.pptagent")
@ConfigurationPropertiesScan(basePackages = "com.pptagent")
@EntityScan(basePackages = "com.pptagent.domain")
@EnableJpaRepositories(basePackages = "com.pptagent.domain")
public class ApiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiServerApplication.class, args);
    }
}

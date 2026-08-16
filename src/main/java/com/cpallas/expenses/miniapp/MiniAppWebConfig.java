package com.cpallas.expenses.miniapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class MiniAppWebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public MiniAppWebConfig(
            @Value("${expense.mini-app.allowed-origins:http://localhost:3000,http://localhost:3001}")
            String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/mini-app/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}

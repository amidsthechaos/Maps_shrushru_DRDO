package com.georoute.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows only the local Angular dev server. No external origins are permitted,
 * consistent with the offline mandate.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
// reads value from application.properties file. If not found, defaults to http://localhost:4200
    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;
// replacing pre existing CORS configurations with a new one.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}

/* 
allows connections from frontend to backend. The frontend is running on localhost:4200, so we allow that origin.

*/
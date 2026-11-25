package com.TradeReport.TradeReport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the browser app (for example the Angular dashboard on http://localhost:4200) call this API.
 *
 * Browsers block a page from calling another origin (a different host or port) unless that server
 * says it is allowed. Before a POST or PUT with a JSON body, the browser first sends an OPTIONS
 * "preflight" request, and these settings are what answer it.
 *
 * Allowed origins come from app.cors.allowed-origins (comma separated). Anything else is rejected.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600); // the browser may remember a preflight answer for an hour
    }
}

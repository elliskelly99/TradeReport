package com.TradeReport.TradeReport.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One real Postgres for the whole test run. Spring Boot starts it once, points the datasource at it
 * (@ServiceConnection), and caches the application context, so every test class that imports this
 * shares the same container. Same image as docker-compose.yml.
 */
@TestConfiguration
public class PostgresTestConfig {

    @Bean
    @ServiceConnection//@ServiceConnection wires the container's URL into the datasource.
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}

package com.pulock.payflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Shared test setup: a real, throwaway Postgres in Docker.
// Use it with @Import(TestcontainersConfiguration.class) on a @SpringBootTest.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same image as docker-compose.yml, so tests run against the database we really use.
    // @ServiceConnection: Spring reads the container's URL/user/password and builds the
    // DataSource from them (no DB_* settings needed). Spring starts and stops the container.
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16");
    }
}

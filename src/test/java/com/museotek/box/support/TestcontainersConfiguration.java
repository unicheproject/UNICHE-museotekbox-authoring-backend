package com.museotek.box.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for {@code @SpringBootTest}s, not an approximation. {@code @ServiceConnection}
 * makes Spring Boot start this container and wire {@code spring.datasource.*} to it itself — no
 * manual {@code @DynamicPropertySource} for the datasource, and no H2 dialect quirks to worry
 * about diverging from what production actually runs against.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}

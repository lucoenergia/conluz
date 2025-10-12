package org.lucoenergia.conluz.infrastructure.shared;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles({"test"})
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test_db")
            .withUsername("luz")
            .withPassword("blank");

    /**
     * Uses GenericContainer since there's no specific Testcontainers support for InfluxDB 3 yet
     * InfluxDB 3 Core runs on port 8181 by default
     * Note: Using forListeningPort() instead of HTTP check because endpoints require authentication
     */
    static final GenericContainer<?> INFLUX_DB3_CONTAINER = new GenericContainer<>(
            DockerImageName.parse("influxdb:3-core")
    )
            .withExposedPorts(8181)
            .withEnv("INFLUXDB3_HTTP_BIND_ADDR", "0.0.0.0:8181")
            .withEnv("LOG_FILTER", "debug")
            .withCommand(
                    "influxdb3", "serve",
                    "--node-id", "conluz_test_node",
                    "--object-store", "memory",
                    "--data-dir", "/var/lib/influxdb3/data"
            )
            .waitingFor(Wait.forListeningPort()); // Simply waits for the container's exposed port (8181) to be accepting connections, without making an HTTP request

    @BeforeAll
    static void setupOnce() {
        if (!POSTGRES_CONTAINER.isRunning()) {
            POSTGRES_CONTAINER.start();
        }
        if (!INFLUX_DB3_CONTAINER.isRunning()) {
            INFLUX_DB3_CONTAINER.start();

            // Create an admin token for InfluxDB 3
            try {
                var result = INFLUX_DB3_CONTAINER.execInContainer("influxdb3", "create", "token", "--admin");
                if (result.getExitCode() == 0) {
                    String output = result.getStdout();
                    // Extract token from output - look for line starting with "Token:"
                    String token = null;
                    for (String line : output.split("\n")) {
                        if (line.trim().startsWith("Token:")) {
                            token = line.substring(line.indexOf(":") + 1).trim();
                            break;
                        }
                    }
                    if (token == null) {
                        throw new RuntimeException("Could not extract token from InfluxDB3 output: " + output);
                    }
                    // Store the token for later use in configureProperties
                    System.setProperty("test.influxdb3.token", token);
                } else {
                    throw new RuntimeException("Failed to create InfluxDB3 admin token: " + result.getStderr());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create InfluxDB3 admin token", e);
            }
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);

        // InfluxDB 3 configuration
        registry.add("spring.influxdb3.url", () ->
                "http://" + INFLUX_DB3_CONTAINER.getHost() + ":" + INFLUX_DB3_CONTAINER.getMappedPort(8181));
        registry.add("spring.influxdb3.token", () -> System.getProperty("test.influxdb3.token", ""));
        registry.add("spring.influxdb3.org", () -> "lucoenergia");
        registry.add("spring.influxdb3.bucket", () -> "conluz_db_test");
    }
}

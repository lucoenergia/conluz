package org.lucoenergia.conluz.infrastructure.shared;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.MockInfluxDbConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles({"test"})
public class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test_db")
            .withUsername("luz")
            .withPassword("blank");

    static final InfluxDBContainer<?> INFLUX_DB_CONTAINER = new InfluxDBContainer<>(
            DockerImageName.parse("influxdb:1.8")
    )
            .withDatabase(MockInfluxDbConfiguration.INFLUX_DB_NAME)
            .withUsername("luz")
            .withPassword("blank")
//            .withAdmin("luz")
//            .withAdminPassword("blank")
            .waitingFor(Wait.forHttp("/ping").forStatusCode(204));

    @BeforeAll
    static void setupOnce() {
        POSTGRES_CONTAINER.start();
        INFLUX_DB_CONTAINER.start();
    }

    @AfterAll
    static void afterAll() {
        POSTGRES_CONTAINER.stop();
        INFLUX_DB_CONTAINER.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);

        registry.add("spring.influxdb.url", INFLUX_DB_CONTAINER::getUrl);
        registry.add("spring.influxdb.username", INFLUX_DB_CONTAINER::getUsername);
        registry.add("spring.influxdb.password", INFLUX_DB_CONTAINER::getPassword);
        registry.add("spring.influxdb.database", INFLUX_DB_CONTAINER::getDatabase);
    }
}

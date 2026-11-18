package com.intersocial.post;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ApplicationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("posts_db");

    @Test
    void startsWithPostgresMigratesAndExposesHealth() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", -1,
                "datasources.default.url", postgres.getJdbcUrl(),
                "datasources.default.username", postgres.getUsername(),
                "datasources.default.password", postgres.getPassword()))) {
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT current_database()")) {
                    assertTrue(result.next());
                    assertEquals("posts_db", result.getString(1));
                }
                try (var result = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
            }
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.getPort() + "/health")).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("UP"));
            }
        }
    }
}

package com.intersocial.post.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    static EmbeddedServer server;
    static HttpClient client;
    static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", -1,
                "datasources.default.url", postgres.getJdbcUrl(),
                "datasources.default.username", postgres.getUsername(),
                "datasources.default.password", postgres.getPassword()));
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() { client.close(); server.close(); }

    @BeforeEach
    void clear() throws Exception { sql("DELETE FROM posts"); }

    static void sql(String query) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) { statement.execute(query); }
    }

    static HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void createsPersistsAndFindsPost() throws Exception {
        var response = request("POST", "/api/posts", "{\"content\":\"Hello world\"}");
        assertEquals(201, response.statusCode());
        JsonNode post = json.readTree(response.body());
        UUID.fromString(post.get("id").asText());
        assertEquals("PUBLISHED", post.get("status").asText());
        assertEquals("Hello world", post.get("content").asText());
        assertNotNull(post.get("createdAt"));
        var found = request("GET", "/api/posts/" + post.get("id").asText(), null);
        assertEquals(200, found.statusCode());
        assertEquals(post, json.readTree(found.body()));
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT content, status, created_at FROM posts")) {
            assertTrue(rows.next());
            assertEquals("Hello world", rows.getString(1));
            assertEquals("PUBLISHED", rows.getString(2));
            assertNotNull(rows.getTimestamp(3));
        }
    }

    @Test
    void rejectsInvalidContentAndIdentifiers() throws Exception {
        for (String body : new String[]{"{}", "{\"content\":null}", "{\"content\":\"\"}",
                "{\"content\":\"   \"}",
                json.writeValueAsString(Map.of("content", "x".repeat(5001))), "{"}) {
            assertEquals(400, request("POST", "/api/posts", body).statusCode(), body);
        }
        assertEquals(400, request("GET", "/api/posts/invalid", null).statusCode());
        assertEquals(404, request("GET", "/api/posts/" + UUID.randomUUID(), null).statusCode());
        assertEquals(201, request("POST", "/api/posts", json.writeValueAsString(Map.of("content", "x".repeat(5000)))).statusCode());
    }

    @Test
    void paginatesOrdersAndFiltersFeed() throws Exception {
        sql("INSERT INTO posts VALUES "
                + "('00000000-0000-0000-0000-000000000001', 'old', 'PUBLISHED', '2026-01-01T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000002', 'second', 'PUBLISHED', '2026-01-02T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000003', 'first', 'PUBLISHED', '2026-01-02T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000004', 'pending', 'PENDING_MODERATION', '2026-01-03T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000005', 'rejected', 'REJECTED', '2026-01-03T00:00:00Z')");
        var response = request("GET", "/api/feed?page=0&size=2", null);
        assertEquals(200, response.statusCode());
        var first = json.readTree(response.body());
        assertEquals(3, first.get("totalElements").asInt());
        assertEquals(2, first.get("totalPages").asInt());
        assertEquals("first", first.get("content").get(0).get("content").asText());
        assertEquals("second", first.get("content").get(1).get("content").asText());
        var second = json.readTree(request("GET", "/api/feed?page=1&size=2", null).body());
        assertEquals(1, second.get("content").size());
        assertEquals("old", second.get("content").get(0).get("content").asText());
        var empty = request("GET", "/api/feed?page=9&size=2", null);
        assertEquals(200, empty.statusCode(), empty.body());
        assertEquals(0, json.readTree(empty.body()).get("content").size());
        for (String query : new String[]{"page=-1", "size=0", "size=101", "page=abc", "size=2147483648"}) {
            assertEquals(400, request("GET", "/api/feed?" + query, null).statusCode(), query);
        }
    }
}

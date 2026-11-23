package com.intersocial.post.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    static EmbeddedServer server;
    static HttpServer mediaServer;
    static final Map<UUID, String> mediaStatuses = new ConcurrentHashMap<>();
    static final UUID UNAVAILABLE_MEDIA = UUID.randomUUID();
    static HttpClient client;
    static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void start() throws IOException {
        mediaServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mediaServer.createContext("/api/media/", exchange -> {
            try (exchange) {
                UUID id = UUID.fromString(exchange.getRequestURI().getPath().substring("/api/media/".length()));
                String status = mediaStatuses.get(id);
                if (id.equals(UNAVAILABLE_MEDIA) || status == null) {
                    exchange.sendResponseHeaders(id.equals(UNAVAILABLE_MEDIA) ? 503 : 404, -1);
                    return;
                }
                byte[] body = json.writeValueAsString(Map.of("id", id, "status", status, "fileName", "fixture.png"))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        mediaServer.start();
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", -1,
                "media.api.base-url", "http://127.0.0.1:" + mediaServer.getAddress().getPort(),
                "moderation.sqs.enabled", false,
                "datasources.default.url", postgres.getJdbcUrl(),
                "datasources.default.username", postgres.getUsername(),
                "datasources.default.password", postgres.getPassword()));
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        if (client != null) client.close();
        if (server != null) server.close();
        if (mediaServer != null) mediaServer.stop(0);
    }

    @BeforeEach
    void clear() throws Exception {
        sql("DELETE FROM image_moderation_outbox");
        sql("DELETE FROM posts");
        mediaStatuses.clear();
    }

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

    static String imagePost(UUID mediaId) throws IOException {
        return json.writeValueAsString(Map.of("content", "Text with image", "mediaId", mediaId));
    }

    static int countRows(String table) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    @Test
    void createsPersistsAndFindsPost() throws Exception {
        var response = request("POST", "/api/posts", "{\"content\":\"Hello world\"}");
        assertEquals(201, response.statusCode());
        JsonNode post = json.readTree(response.body());
        UUID.fromString(post.get("id").asText());
        assertEquals("PUBLISHED", post.get("status").asText());
        assertFalse(post.has("mediaId"));
        assertEquals(0, countRows("image_moderation_outbox"));
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
    void createsPendingPostAndEventAndKeepsItOutOfFeed() throws Exception {
        UUID mediaId = UUID.randomUUID();
        mediaStatuses.put(mediaId, "UPLOADED");

        var response = request("POST", "/api/posts", imagePost(mediaId));
        assertEquals(201, response.statusCode(), response.body());
        JsonNode post = json.readTree(response.body());
        String path = response.headers().firstValue("Location").orElseThrow();
        assertEquals("/api/posts/" + post.get("id").asText(), path);
        assertEquals("PENDING_MODERATION", post.get("status").asText());
        assertEquals(mediaId.toString(), post.get("mediaId").asText());
        assertEquals(post, json.readTree(request("GET", path, null).body()));
        var feed = json.readTree(request("GET", "/api/feed", null).body());
        assertEquals(0, feed.get("totalElements").asInt());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT posts.status, posts.media_id, posts.created_at, outbox.event_id, outbox.payload, outbox.published_at
                     FROM posts JOIN image_moderation_outbox outbox ON posts.id = outbox.post_id
                     """)) {
            assertTrue(rows.next());
            assertEquals("PENDING_MODERATION", rows.getString("status"));
            assertEquals(mediaId, rows.getObject("media_id", UUID.class));
            assertNull(rows.getObject("published_at"));
            assertEquals(json.valueToTree(Map.of(
                    "eventId", rows.getString("event_id"), "eventType", "ImageModerationRequested",
                    "eventVersion", 1, "occurredAt", rows.getObject("created_at", OffsetDateTime.class).toInstant().toString(),
                    "postId", post.get("id").asText(), "mediaId", mediaId.toString()
            )), json.readTree(rows.getString("payload")));
            assertFalse(rows.next());
        }
    }

    @Test
    void rejectsInvalidMissingUnconfirmedAndUnavailableMediaWithoutWrites() throws Exception {
        for (var invalid : new String[]{"", "invalid", "1-1-1-1-1", "AAAAAAAAAAAAAAAAAAAAAA=="}) {
            var body = json.writeValueAsString(Map.of("content", "Text", "mediaId", invalid));
            assertEquals(400, request("POST", "/api/posts", body).statusCode(), body);
        }
        assertEquals(404, request("POST", "/api/posts", imagePost(UUID.randomUUID())).statusCode());
        UUID pending = UUID.randomUUID();
        mediaStatuses.put(pending, "PENDING_UPLOAD");
        assertEquals(409, request("POST", "/api/posts", imagePost(pending)).statusCode());
        assertEquals(503, request("POST", "/api/posts", imagePost(UNAVAILABLE_MEDIA)).statusCode());
        assertEquals(0, countRows("posts"));
        assertEquals(0, countRows("image_moderation_outbox"));
    }

    @Test
    void allowsOnlyOnePostForTheSameMediaEvenWithConcurrentRequests() throws Exception {
        UUID mediaId = UUID.randomUUID();
        mediaStatuses.put(mediaId, "UPLOADED");
        var postRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/api/posts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(imagePost(mediaId))).build();
        var first = client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();
        assertEquals(Set.of(201, 409), Set.copyOf(List.of(first.join().statusCode(), second.join().statusCode())));
        assertEquals(1, countRows("posts"));
        assertEquals(1, countRows("image_moderation_outbox"));
    }

    @Test
    void rollsBackPostIfOutboxInsertFails() throws Exception {
        UUID mediaId = UUID.randomUUID();
        mediaStatuses.put(mediaId, "UPLOADED");
        sql("ALTER TABLE image_moderation_outbox ADD CONSTRAINT test_reject_insert CHECK (false) NOT VALID");
        try {
            assertEquals(500, request("POST", "/api/posts", imagePost(mediaId)).statusCode());
            assertEquals(0, countRows("posts"));
            assertEquals(0, countRows("image_moderation_outbox"));
        } finally {
            sql("ALTER TABLE image_moderation_outbox DROP CONSTRAINT test_reject_insert");
        }
        assertEquals(201, request("POST", "/api/posts", imagePost(mediaId)).statusCode());
    }

    @Test
    void paginatesOrdersAndFiltersFeed() throws Exception {
        sql("INSERT INTO posts VALUES "
                + "('00000000-0000-0000-0000-000000000001', 'old', 'PUBLISHED', '2026-01-01T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000002', 'second', 'PUBLISHED', '2026-01-02T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000003', 'first', 'APPROVED', '2026-01-02T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000004', 'pending', 'PENDING_MODERATION', '2026-01-03T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000005', 'rejected', 'REJECTED', '2026-01-03T00:00:00Z'),"
                + "('00000000-0000-0000-0000-000000000006', 'review', 'REVIEW_REQUIRED', '2026-01-03T00:00:00Z')");
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

package com.intersocial.media.media.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intersocial.media.media.entity.MediaStatus;
import com.intersocial.media.media.repository.MediaRepository;
import com.intersocial.media.media.storage.MediaStorage;
import com.intersocial.media.media.storage.S3MediaStorage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MediaApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("media_db");

    private static final String UPLOADS = "/api/media/uploads";

    @Test
    void authorizesUniqueUploadsAndPersistsMetadata() throws Exception {
        try (var api = new Api()) {
            var first = api.create("image/jpeg", 100);
            var second = api.create("image/png", 10);
            var firstId = UUID.fromString(first.path("mediaId").asText());
            var secondId = UUID.fromString(second.path("mediaId").asText());
            assertNotEquals(firstId, secondId);
            assertEquals("PUT", first.path("method").asText());
            assertEquals("image/jpeg", first.path("requiredHeaders").path("Content-Type").asText());
            assertEquals("*", first.path("requiredHeaders").path("If-None-Match").asText());
            assertEquals(api.storage.expiresAt, Instant.parse(first.path("expiresAt").asText()));

            var repository = api.context.getBean(MediaRepository.class);
            var media = repository.findById(firstId).orElseThrow();
            assertEquals(MediaStatus.PENDING_UPLOAD, media.status());
            assertEquals("../foto.jpg", media.fileName());
            assertEquals("image/jpeg", media.contentType());
            assertEquals(100, media.size());
            assertEquals("test-bucket", media.bucket());
            assertEquals("uploads/" + firstId, media.objectKey());
            assertNotEquals(media.objectKey(), repository.findById(secondId).orElseThrow().objectKey());
            assertEquals(media.createdAt(), media.updatedAt());

            var metadata = api.json(api.get("/api/media/" + firstId), 200);
            assertEquals("PENDING_UPLOAD", metadata.path("status").asText());
            assertFalse(metadata.has("uploadUrl"));
            assertFalse(metadata.has("objectKey"));
        }
    }

    @Test
    void validatesInputAndHandlesMissingMedia() throws Exception {
        try (var api = new Api()) {
            assertEquals(400, api.post(UPLOADS, body("image/gif", 10)).statusCode());
            assertEquals(400, api.post(UPLOADS, body("image/jpeg", 0)).statusCode());
            assertEquals(400, api.post(UPLOADS, body("image/png", 101)).statusCode());
            assertEquals(400, api.post(UPLOADS, "{}").statusCode());
            var missing = "/api/media/" + UUID.randomUUID();
            assertEquals(404, api.get(missing).statusCode());
            assertEquals(404, api.post(missing + "/confirm", null).statusCode());
        }
    }

    @Test
    void confirmsOnlyMatchingObjectsAndKeepsRepeatedConfirmationStable() throws Exception {
        try (var api = new Api()) {
            var id = api.create("image/png", 10).path("mediaId").asText();
            var key = "uploads/" + id;
            var media = "/api/media/" + id;
            var confirm = media + "/confirm";
            assertEquals(409, api.post(confirm, null).statusCode());
            api.storage.objects.put(key, new MediaStorage.ObjectMetadata(11, "image/png"));
            assertEquals(422, api.post(confirm, null).statusCode());
            api.storage.objects.put(key, new MediaStorage.ObjectMetadata(10, "image/jpeg"));
            assertEquals(422, api.post(confirm, null).statusCode());
            assertEquals("PENDING_UPLOAD", api.json(api.get(media), 200).path("status").asText());

            api.storage.objects.put(key, new MediaStorage.ObjectMetadata(10, "image/png"));
            var confirmed = api.json(api.post(confirm, null), 200);
            assertEquals("UPLOADED", confirmed.path("status").asText());
            api.storage.unavailable = true;
            assertEquals(confirmed, api.json(api.post(confirm, null), 200));
            assertEquals(confirmed, api.json(api.get(media), 200));
        }
    }

    @Test
    void reportsStorageFailuresWithoutCreatingOrConfirmingMedia() throws Exception {
        try (var api = new Api()) {
            var id = api.create("image/png", 10).path("mediaId").asText();
            var repository = api.context.getBean(MediaRepository.class);
            var count = repository.count();
            api.storage.unavailable = true;
            assertEquals(503, api.post(UPLOADS, body("image/png", 10)).statusCode());
            assertEquals(count, repository.count());
            assertEquals(503, api.post("/api/media/" + id + "/confirm", null).statusCode());
            assertEquals(MediaStatus.PENDING_UPLOAD, repository.findById(UUID.fromString(id)).orElseThrow().status());
            assertEquals(200, api.get("/health").statusCode());
        }
    }

    private static String body(String contentType, long size) {
        return """
                {"fileName":"../foto.jpg","contentType":"%s","size":%d}
                """.formatted(contentType, size);
    }

    private static final class Api implements AutoCloseable {
        final FakeStorage storage;
        final ApplicationContext context = ApplicationContext.builder().properties(Map.of(
                "micronaut.server.port", -1,
                "datasources.default.url", postgres.getJdbcUrl(),
                "datasources.default.username", postgres.getUsername(),
                "datasources.default.password", postgres.getPassword(),
                "test.media.fake-storage", true,
                "media.s3.bucket", "test-bucket",
                "media.upload.max-bytes", 100)).build();
        final EmbeddedServer server;
        final HttpClient client = HttpClient.newHttpClient();
        final ObjectMapper mapper;

        Api() {
            context.start();
            storage = context.getBean(FakeStorage.class);
            server = context.getBean(EmbeddedServer.class).start();
            mapper = context.getBean(ObjectMapper.class);
        }

        JsonNode create(String contentType, long size) throws Exception {
            var response = post(UPLOADS, body(contentType, size));
            var upload = json(response, 201);
            assertEquals("/api/media/" + upload.path("mediaId").asText(),
                    response.headers().firstValue("Location").orElseThrow());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            return upload;
        }

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(server.getURI().resolve(path)).GET().build());
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            var request = HttpRequest.newBuilder(server.getURI().resolve(path));
            if (body == null) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            }
            return send(request.build());
        }

        private HttpResponse<String> send(HttpRequest request) throws Exception {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        JsonNode json(HttpResponse<String> response, int status) throws Exception {
            assertEquals(status, response.statusCode(), response.body());
            return mapper.readTree(response.body());
        }

        @Override
        public void close() {
            client.close();
            server.close();
            context.close();
        }
    }

    @Factory
    @Requires(property = "test.media.fake-storage", value = "true")
    static class FakeStorageFactory {
        @Singleton
        @Replaces(S3MediaStorage.class)
        FakeStorage fakeStorage() {
            return new FakeStorage();
        }
    }

    static final class FakeStorage implements MediaStorage {
        final Instant expiresAt = Instant.now().plusSeconds(300);
        final Map<String, ObjectMetadata> objects = new ConcurrentHashMap<>();
        volatile boolean unavailable;

        @Override
        public Upload presign(String bucket, String key, String contentType) {
            if (unavailable) {
                throw new UnavailableException();
            }
            return new Upload("https://example.invalid/" + bucket + "/" + key,
                    Map.of("Content-Type", contentType, "If-None-Match", "*"), expiresAt);
        }

        @Override
        public Optional<ObjectMetadata> head(String bucket, String key) {
            if (unavailable) {
                throw new UnavailableException();
            }
            return Optional.ofNullable(objects.get(key));
        }
    }
}

package com.intersocial.post.post.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Singleton
public class MediaClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI baseUrl;
    private final JsonMapper json;

    public MediaClient(@Value("${media.api.base-url}") URI baseUrl, JsonMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    public void requireUploaded(UUID mediaId) {
        var request = HttpRequest.newBuilder(baseUrl.resolve("/api/media/" + mediaId))
                .timeout(Duration.ofSeconds(3)).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new HttpStatusException(HttpStatus.NOT_FOUND, "Media not found");
            }
            if (response.statusCode() != 200) {
                throw unavailable();
            }

            var media = json.readValue(response.body(), MediaMetadata.class);
            if (media == null || !mediaId.equals(media.id()) || media.status() == null) {
                throw unavailable();
            }
            if (!"UPLOADED".equals(media.status())) {
                throw new HttpStatusException(HttpStatus.CONFLICT, "Media upload is not confirmed");
            }
        } catch (IOException exception) {
            throw unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static HttpStatusException unavailable() {
        return new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media service unavailable");
    }

    @PreDestroy
    void close() {
        client.close();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaMetadata(UUID id, String status) {}
}

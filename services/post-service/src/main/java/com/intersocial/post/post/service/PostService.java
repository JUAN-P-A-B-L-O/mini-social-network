package com.intersocial.post.post.service;

import com.intersocial.post.messaging.dto.ImageModerationRequested;
import com.intersocial.post.messaging.entity.OutboxEntry;
import com.intersocial.post.messaging.repository.OutboxRepository;
import com.intersocial.post.post.client.MediaClient;
import com.intersocial.post.post.dto.CreatePostRequest;
import com.intersocial.post.post.dto.PostResponse;
import com.intersocial.post.post.entity.Post;
import com.intersocial.post.post.entity.PostStatus;
import com.intersocial.post.post.mapper.PostMapper;
import com.intersocial.post.post.repository.PostRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.json.JsonMapper;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Singleton
public class PostService {
    private final PostRepository repository;
    private final MediaClient media;
    private final OutboxRepository outbox;
    private final TransactionOperations<Connection> transactions;
    private final JsonMapper json;

    public PostService(PostRepository repository, MediaClient media, OutboxRepository outbox,
                       TransactionOperations<Connection> transactions, JsonMapper json) {
        this.repository = repository;
        this.media = media;
        this.outbox = outbox;
        this.transactions = transactions;
        this.json = json;
    }

    public PostResponse create(CreatePostRequest request) {
        var id = UUID.randomUUID();
        var createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (request.mediaId() == null) {
            return PostMapper.toResponse(repository.save(
                    new Post(id, request.content(), PostStatus.PUBLISHED, createdAt, null)));
        }

        var mediaId = UUID.fromString(request.mediaId());
        media.requireUploaded(mediaId);

        return transactions.executeWrite(transaction -> {
            if (repository.insertPending(id, request.content(), createdAt, mediaId) == 0) {
                throw new HttpStatusException(HttpStatus.CONFLICT, "Media is already linked to a post");
            }
            var event = new ImageModerationRequested(id, mediaId, createdAt);
            outbox.save(new OutboxEntry(event.eventId(), id, serialize(event), 0));
            return PostMapper.toResponse(new Post(id, request.content(), PostStatus.PENDING_MODERATION, createdAt, mediaId));
        });
    }

    public PostResponse find(UUID id) {
        return repository.findById(id).map(PostMapper::toResponse)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private String serialize(ImageModerationRequested event) {
        try {
            return json.writeValueAsString(event);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not serialize moderation event", exception);
        }
    }
}

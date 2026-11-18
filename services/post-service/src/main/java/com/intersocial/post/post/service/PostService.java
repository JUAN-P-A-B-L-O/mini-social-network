package com.intersocial.post.post.service;

import com.intersocial.post.post.dto.CreatePostRequest;
import com.intersocial.post.post.dto.PostResponse;
import com.intersocial.post.post.entity.Post;
import com.intersocial.post.post.entity.PostStatus;
import com.intersocial.post.post.mapper.PostMapper;
import com.intersocial.post.post.repository.PostRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Singleton
public class PostService {
    private final PostRepository repository;

    public PostService(PostRepository repository) { this.repository = repository; }

    public PostResponse create(CreatePostRequest request) {
        return PostMapper.toResponse(repository.save(new Post(UUID.randomUUID(), request.content(),
                PostStatus.PUBLISHED, Instant.now().truncatedTo(ChronoUnit.MICROS))));
    }

    public PostResponse find(UUID id) {
        return repository.findById(id).map(PostMapper::toResponse)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }
}

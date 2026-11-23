package com.intersocial.post.feed.service;

import com.intersocial.post.feed.dto.FeedResponse;
import com.intersocial.post.post.mapper.PostMapper;
import com.intersocial.post.post.repository.PostRepository;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

@Singleton
public class FeedService {
    private final PostRepository repository;

    public FeedService(PostRepository repository) { this.repository = repository; }

    public FeedResponse find(int page, int size) {
        var posts = repository.findVisible(Pageable.from(page, size));
        return new FeedResponse(posts.getContent().stream().map(PostMapper::toResponse).toList(),
                page, size, posts.getTotalSize(), posts.getTotalPages());
    }
}

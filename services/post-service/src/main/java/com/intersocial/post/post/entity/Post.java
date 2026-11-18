package com.intersocial.post.post.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;
import java.util.UUID;

@MappedEntity("posts")
public record Post(@Id UUID id, String content, PostStatus status, Instant createdAt) {}

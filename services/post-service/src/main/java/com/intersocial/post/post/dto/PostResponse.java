package com.intersocial.post.post.dto;

import com.intersocial.post.post.entity.PostStatus;
import java.time.Instant;
import java.util.UUID;

public record PostResponse(UUID id, String content, PostStatus status, Instant createdAt) {}

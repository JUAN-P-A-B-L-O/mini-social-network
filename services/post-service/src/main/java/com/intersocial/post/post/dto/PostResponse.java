package com.intersocial.post.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.intersocial.post.post.entity.PostStatus;
import java.time.Instant;
import java.util.UUID;

public record PostResponse(UUID id, String content, PostStatus status, Instant createdAt,
                           @JsonInclude(JsonInclude.Include.NON_NULL) UUID mediaId) {}

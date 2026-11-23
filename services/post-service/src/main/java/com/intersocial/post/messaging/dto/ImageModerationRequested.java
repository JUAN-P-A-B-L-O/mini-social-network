package com.intersocial.post.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record ImageModerationRequested(
        UUID eventId,
        String eventType,
        int eventVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        UUID postId,
        UUID mediaId
) {
    public ImageModerationRequested(UUID postId, UUID mediaId, Instant occurredAt) {
        this(UUID.randomUUID(), "ImageModerationRequested", 1, occurredAt, postId, mediaId);
    }
}

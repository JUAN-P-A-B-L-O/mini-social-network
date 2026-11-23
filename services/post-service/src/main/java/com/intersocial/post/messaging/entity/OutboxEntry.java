package com.intersocial.post.messaging.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.UUID;

@MappedEntity("image_moderation_outbox")
public record OutboxEntry(@Id UUID eventId, UUID postId, String payload, int attempts) {}

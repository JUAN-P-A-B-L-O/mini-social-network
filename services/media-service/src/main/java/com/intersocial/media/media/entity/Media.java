package com.intersocial.media.media.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;
import java.util.UUID;

@MappedEntity("media")
public record Media(@Id UUID id, String fileName, String contentType, long size,
                    String bucket, String objectKey, MediaStatus status,
                    Instant createdAt, Instant updatedAt) {}

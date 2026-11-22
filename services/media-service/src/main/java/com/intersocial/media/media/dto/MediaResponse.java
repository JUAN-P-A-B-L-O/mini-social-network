package com.intersocial.media.media.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.intersocial.media.media.entity.MediaStatus;
import java.time.Instant;
import java.util.UUID;

public record MediaResponse(UUID id, String fileName, String contentType, long size,
                            MediaStatus status,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {}

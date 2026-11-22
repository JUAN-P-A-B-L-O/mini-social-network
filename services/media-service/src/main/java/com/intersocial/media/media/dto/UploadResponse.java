package com.intersocial.media.media.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UploadResponse(UUID mediaId, String uploadUrl, String method,
                             Map<String, String> requiredHeaders,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt) {}

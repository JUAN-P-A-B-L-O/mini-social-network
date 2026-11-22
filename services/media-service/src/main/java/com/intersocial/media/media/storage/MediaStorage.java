package com.intersocial.media.media.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface MediaStorage {
    Upload presign(String bucket, String key, String contentType);

    Optional<ObjectMetadata> head(String bucket, String key);

    record Upload(String url, Map<String, String> headers, Instant expiresAt) {}

    record ObjectMetadata(long size, String contentType) {}

    final class UnavailableException extends RuntimeException {}
}

package com.intersocial.media.media.service;

import com.intersocial.media.media.dto.CreateUploadRequest;
import com.intersocial.media.media.dto.MediaResponse;
import com.intersocial.media.media.dto.UploadResponse;
import com.intersocial.media.media.entity.Media;
import com.intersocial.media.media.entity.MediaStatus;
import com.intersocial.media.media.repository.MediaRepository;
import com.intersocial.media.media.storage.MediaStorage;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Singleton
public class MediaService {
    private final MediaRepository repository;
    private final MediaStorage storage;
    private final String bucket;
    private final long maxSize;

    public MediaService(MediaRepository repository, MediaStorage storage,
                        @Value("${media.s3.bucket}") String bucket,
                        @Value("${media.upload.max-bytes}") long maxSize) {
        this.repository = repository;
        this.storage = storage;
        this.bucket = bucket;
        this.maxSize = maxSize;
    }

    public UploadResponse createUpload(CreateUploadRequest request) {
        if (request.size() > maxSize) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Image exceeds the configured size limit");
        }
        var id = UUID.randomUUID();
        var key = "uploads/" + id;
        try {
            var upload = storage.presign(bucket, key, request.contentType());
            var now = timestamp();
            repository.save(new Media(id, request.fileName(), request.contentType(), request.size(),
                    bucket, key, MediaStatus.PENDING_UPLOAD, now, now));
            return new UploadResponse(id, upload.url(), "PUT", upload.headers(), upload.expiresAt());
        } catch (MediaStorage.UnavailableException exception) {
            throw storageUnavailable();
        }
    }

    public MediaResponse confirm(UUID id) {
        var media = requireMedia(id);
        if (media.status() == MediaStatus.UPLOADED) {
            return toResponse(media);
        }

        try {
            var object = storage.head(media.bucket(), media.objectKey())
                    .orElseThrow(() -> new HttpStatusException(HttpStatus.CONFLICT, "Uploaded object not found"));
            if (object.size() != media.size() || !media.contentType().equals(object.contentType())) {
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Uploaded object does not match the declared size or Content-Type");
            }
            repository.markUploaded(id, timestamp());
            return find(id);
        } catch (MediaStorage.UnavailableException exception) {
            throw storageUnavailable();
        }
    }

    public MediaResponse find(UUID id) {
        return toResponse(requireMedia(id));
    }

    private Media requireMedia(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Media not found"));
    }

    private static MediaResponse toResponse(Media media) {
        return new MediaResponse(media.id(), media.fileName(), media.contentType(), media.size(),
                media.status(), media.createdAt(), media.updatedAt());
    }

    private static Instant timestamp() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static HttpStatusException storageUnavailable() {
        return new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is unavailable");
    }
}

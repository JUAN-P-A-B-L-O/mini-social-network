package com.intersocial.media.media.storage;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Singleton
public class S3MediaStorage implements MediaStorage {
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    private final S3Presigner presigner;
    private final S3Client client;

    @Inject
    public S3MediaStorage(@Value("${media.s3.region}") String region) {
        this(region.isBlank() ? null : presignerBuilder(region).build(),
                region.isBlank() ? null : S3Client.builder().region(Region.of(region))
                        .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10))
                                .apiCallAttemptTimeout(Duration.ofSeconds(4)))
                        .build());
    }

    S3MediaStorage(S3Presigner presigner, S3Client client) {
        this.presigner = presigner;
        this.client = client;
    }

    static S3Presigner.Builder presignerBuilder(String region) {
        return S3Presigner.builder().region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().checksumValidationEnabled(false).build());
    }

    @Override
    public Upload presign(String bucket, String key, String contentType) {
        requireConfigured(bucket);
        try {
            var request = presigner.presignPutObject(signing -> signing.signatureDuration(UPLOAD_TTL)
                    .putObjectRequest(put -> put.bucket(bucket).key(key)
                            .contentType(contentType).ifNoneMatch("*")));
            return new Upload(request.url().toExternalForm(),
                    Map.of("Content-Type", contentType, "If-None-Match", "*"), request.expiration());
        } catch (SdkException exception) {
            throw new UnavailableException();
        }
    }

    @Override
    public Optional<ObjectMetadata> head(String bucket, String key) {
        requireConfigured(bucket);
        try {
            var object = client.headObject(head -> head.bucket(bucket).key(key));
            return Optional.of(new ObjectMetadata(object.contentLength(), object.contentType()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw new UnavailableException();
        } catch (SdkException exception) {
            throw new UnavailableException();
        }
    }

    private void requireConfigured(String bucket) {
        if (presigner == null || bucket.isBlank()) {
            throw new UnavailableException();
        }
    }

    @PreDestroy
    void close() {
        try {
            if (presigner != null) {
                presigner.close();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}

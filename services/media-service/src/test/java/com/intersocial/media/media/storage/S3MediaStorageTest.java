package com.intersocial.media.media.storage;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.junit.jupiter.api.Assertions.*;

class S3MediaStorageTest {
    @Test
    void signsTheObjectPathAndUploadHeadersForFiveMinutes() {
        try (var signer = S3MediaStorage.presignerBuilder("us-east-1")
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            var before = Instant.now().minusSeconds(1);
            var upload = new S3MediaStorage(signer, null).presign("lab-test-bucket", "uploads/test-id", "image/png");
            var url = URI.create(upload.url());
            assertEquals("https", url.getScheme());
            assertTrue(url.getHost().startsWith("lab-test-bucket.s3"));
            assertEquals("/uploads/test-id", url.getPath());
            assertTrue(url.getQuery().contains("X-Amz-Expires=300"));
            assertTrue(url.getQuery().contains("X-Amz-SignedHeaders=content-type;host;if-none-match"));
            assertTrue(url.getQuery().contains("X-Amz-Signature="));
            assertFalse(upload.url().contains("test-secret-key"));
            assertEquals("image/png", upload.headers().get("Content-Type"));
            assertEquals("*", upload.headers().get("If-None-Match"));
            assertFalse(upload.expiresAt().isBefore(before.plusSeconds(300)));
            assertFalse(upload.expiresAt().isAfter(Instant.now().plusSeconds(300)));
        }
    }

    @Test
    void rejectsStorageOperationsWithoutConfiguration() {
        var storage = new S3MediaStorage("");
        assertThrows(MediaStorage.UnavailableException.class,
                () -> storage.presign("", "uploads/test-id", "image/png"));
        assertThrows(MediaStorage.UnavailableException.class,
                () -> storage.head("lab-test-bucket", "uploads/test-id"));
    }
}

package com.intersocial.post.messaging.service;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Factory
@Requires(property = "moderation.sqs.enabled", value = "true")
class SqsClientFactory {
    @Bean(preDestroy = "close")
    @Singleton
    DefaultCredentialsProvider credentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean(preDestroy = "close")
    @Singleton
    SqsClient sqsClient(@Value("${moderation.sqs.region}") String region,
                        DefaultCredentialsProvider credentials) {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2)))
                .build();
    }
}

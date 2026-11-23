package com.intersocial.post.messaging.service;

import com.intersocial.post.messaging.repository.OutboxRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;

@Singleton
@Requires(property = "moderation.sqs.enabled", value = "true")
public class OutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 10;
    private final TransactionOperations<Connection> transactions;
    private final OutboxRepository outbox;
    private final SqsClient sqs;
    private final String queueUrl;

    public OutboxPublisher(TransactionOperations<Connection> transactions, OutboxRepository outbox,
                           SqsClient sqs, @Value("${moderation.sqs.queue-url}") String queueUrl) {
        if (queueUrl.isBlank()) {
            throw new IllegalArgumentException("MODERATION_QUEUE_URL is required when SQS is enabled");
        }
        this.transactions = transactions;
        this.outbox = outbox;
        this.sqs = sqs;
        this.queueUrl = queueUrl;
    }

    @Scheduled(initialDelay = "5s", fixedDelay = "5s")
    void publishDue() {
        for (int processed = 0; processed < BATCH_SIZE; processed++) {
            if (!publishNext()) {
                return;
            }
        }
    }

    boolean publishNext() {
        return transactions.executeWrite(transaction -> {
            var next = outbox.findNextDue();
            if (next.isEmpty()) {
                return false;
            }

            var entry = next.get();
            try {
                sqs.sendMessage(request -> request.queueUrl(queueUrl).messageBody(entry.payload()));
            } catch (SdkException exception) {
                int delaySeconds = Math.min(900, 5 << Math.min(entry.attempts(), 8));
                outbox.updateRetry(entry.eventId(), delaySeconds);
                LOG.warn("Moderation event {} could not be sent to SQS ({}). Retrying in {}s.",
                        entry.eventId(), exception.getClass().getSimpleName(), delaySeconds);
                return true;
            }

            outbox.markPublished(entry.eventId());
            return true;
        });
    }
}

package com.intersocial.post.messaging.repository;

import com.intersocial.post.messaging.entity.OutboxEntry;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface OutboxRepository extends GenericRepository<OutboxEntry, UUID> {
    OutboxEntry save(OutboxEntry entry);

    @Query("""
            SELECT event_id, post_id, payload, attempts FROM image_moderation_outbox
            WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, event_id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    Optional<OutboxEntry> findNextDue();

    @Query("""
            UPDATE image_moderation_outbox SET published_at = statement_timestamp()
            WHERE event_id = :eventId
            """)
    void markPublished(UUID eventId);

    @Query("""
            UPDATE image_moderation_outbox
            SET attempts = attempts + 1,
                next_attempt_at = statement_timestamp() + :delaySeconds * INTERVAL '1 second'
            WHERE event_id = :eventId
            """)
    void updateRetry(UUID eventId, int delaySeconds);
}

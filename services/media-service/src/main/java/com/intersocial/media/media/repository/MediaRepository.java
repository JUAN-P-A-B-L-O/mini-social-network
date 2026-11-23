package com.intersocial.media.media.repository;

import com.intersocial.media.media.entity.Media;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MediaRepository extends CrudRepository<Media, UUID> {
    @Query("""
            UPDATE media SET status = 'UPLOADED', updated_at = GREATEST(updated_at, :updatedAt)
            WHERE id = :id AND status = 'PENDING_UPLOAD'
            """)
    void markUploaded(UUID id, Instant updatedAt);
}

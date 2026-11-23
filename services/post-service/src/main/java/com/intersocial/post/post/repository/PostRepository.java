package com.intersocial.post.post.repository;

import com.intersocial.post.post.entity.Post;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostRepository extends CrudRepository<Post, UUID> {
    @Query("""
            INSERT INTO posts (id, content, status, created_at, media_id)
            VALUES (:id, :content, 'PENDING_MODERATION', :createdAt, :mediaId)
            ON CONFLICT (media_id) DO NOTHING
            """)
    int insertPending(UUID id, String content, Instant createdAt, UUID mediaId);

    @Query(value = """
            SELECT id, content, status, created_at, media_id FROM posts
            WHERE status IN ('PUBLISHED', 'APPROVED')
            ORDER BY created_at DESC, id DESC
            """, countQuery = """
            SELECT count(*) FROM posts WHERE status IN ('PUBLISHED', 'APPROVED')
            """)
    Page<Post> findVisible(Pageable pageable);
}

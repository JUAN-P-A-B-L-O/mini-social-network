package com.intersocial.post.post.repository;

import com.intersocial.post.post.entity.Post;
import com.intersocial.post.post.entity.PostStatus;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostRepository extends CrudRepository<Post, UUID> {
    Page<Post> findByStatus(PostStatus status, Pageable pageable);
}

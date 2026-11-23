package com.intersocial.post.post.mapper;

import com.intersocial.post.post.dto.PostResponse;
import com.intersocial.post.post.entity.Post;

public final class PostMapper {
    private PostMapper() {}

    public static PostResponse toResponse(Post post) {
        return new PostResponse(post.id(), post.content(), post.status(), post.createdAt(), post.mediaId());
    }
}

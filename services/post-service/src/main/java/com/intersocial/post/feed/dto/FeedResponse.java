package com.intersocial.post.feed.dto;

import com.intersocial.post.post.dto.PostResponse;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedResponse(List<PostResponse> content, int page, int size, long totalElements, int totalPages) {}

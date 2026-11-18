package com.intersocial.post.post.dto;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Introspected
public record CreatePostRequest(@NotBlank @Size(max = 5000) String content) {}

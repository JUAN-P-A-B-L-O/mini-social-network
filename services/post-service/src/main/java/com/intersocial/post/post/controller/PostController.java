package com.intersocial.post.post.controller;

import com.intersocial.post.post.dto.CreatePostRequest;
import com.intersocial.post.post.dto.PostResponse;
import com.intersocial.post.post.service.PostService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

@Validated
@Controller("/api/posts")
@ExecuteOn(TaskExecutors.BLOCKING)
public class PostController {
    private final PostService service;

    public PostController(PostService service) { this.service = service; }

    @Post
    public HttpResponse<PostResponse> create(@Body @Valid CreatePostRequest request) {
        var post = service.create(request);
        return HttpResponse.created(post, URI.create("/api/posts/" + post.id()));
    }

    @Get("/{id}")
    public PostResponse find(UUID id) { return service.find(id); }
}

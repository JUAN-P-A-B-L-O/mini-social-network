package com.intersocial.post.feed.controller;

import com.intersocial.post.feed.dto.FeedResponse;
import com.intersocial.post.feed.service.FeedService;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@Controller("/api/feed")
@ExecuteOn(TaskExecutors.BLOCKING)
public class FeedController {
    private final FeedService service;

    public FeedController(FeedService service) { this.service = service; }

    @Get
    public FeedResponse find(@QueryValue(defaultValue = "0") @Min(0) int page,
                             @QueryValue(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.find(page, size);
    }
}

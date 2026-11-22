package com.intersocial.media.media.controller;

import com.intersocial.media.media.dto.CreateUploadRequest;
import com.intersocial.media.media.dto.MediaResponse;
import com.intersocial.media.media.dto.UploadResponse;
import com.intersocial.media.media.service.MediaService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

@Validated
@Controller("/api/media")
@ExecuteOn(TaskExecutors.BLOCKING)
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @Post("/uploads")
    public HttpResponse<UploadResponse> create(@Body @Valid CreateUploadRequest request) {
        var upload = service.createUpload(request);
        return HttpResponse.created(upload, URI.create("/api/media/" + upload.mediaId()))
                .header("Cache-Control", "no-store");
    }

    @Post("/{id}/confirm")
    public MediaResponse confirm(UUID id) {
        return service.confirm(id);
    }

    @Get("/{id}")
    public MediaResponse find(UUID id) {
        return service.find(id);
    }
}

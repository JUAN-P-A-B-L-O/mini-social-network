package com.intersocial.media.media.dto;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Introspected
public record CreateUploadRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\p{Cntrl}]+") String fileName,
        @NotNull @Pattern(regexp = "image/(jpeg|png)") String contentType,
        @NotNull @Positive Long size) {}

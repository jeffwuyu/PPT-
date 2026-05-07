package com.pptagent.api.file.dto;

import com.pptagent.domain.file.FileType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadTokenRequest(
        @NotNull FileType fileType,
        @NotBlank String originalFilename,
        @NotBlank String contentType,
        @Min(1) long byteSize
) {
}

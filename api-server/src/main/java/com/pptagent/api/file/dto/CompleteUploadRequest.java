package com.pptagent.api.file.dto;

import jakarta.validation.constraints.NotNull;

public record CompleteUploadRequest(@NotNull Long fileId) {
}

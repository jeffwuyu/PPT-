package com.pptagent.api.file.dto;

import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.FileType;
import com.pptagent.domain.file.FileUploadMode;

public record FileAssetResponse(
        Long id,
        FileType fileType,
        String originalFilename,
        String contentType,
        long byteSize,
        String objectKey,
        String publicUrl,
        FileAssetStatus status,
        FileUploadMode uploadMode,
        String failureReason
) {
}

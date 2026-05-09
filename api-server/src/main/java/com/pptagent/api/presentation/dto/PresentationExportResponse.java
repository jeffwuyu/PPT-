package com.pptagent.api.presentation.dto;

import com.pptagent.domain.presentation.PresentationExportStatus;

import java.util.List;

public record PresentationExportResponse(
        Long exportId,
        Long presentationId,
        int versionNumber,
        PresentationExportStatus status,
        Long pptxFileId,
        Long coverFileId,
        List<Long> thumbnailFileIds,
        String pptxUrl,
        String coverUrl,
        List<String> thumbnailUrls,
        String failureReason
) {
}

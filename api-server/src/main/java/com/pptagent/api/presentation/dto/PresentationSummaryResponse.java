package com.pptagent.api.presentation.dto;

import com.pptagent.domain.presentation.PresentationStatus;

import java.time.Instant;

public record PresentationSummaryResponse(
        Long id,
        String title,
        int pageWidth,
        int pageHeight,
        PresentationStatus status,
        int currentVersionNumber,
        Instant createdAt,
        Instant updatedAt,
        long slideCount,
        boolean canEdit
) {
}

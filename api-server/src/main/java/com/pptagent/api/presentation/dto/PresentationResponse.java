package com.pptagent.api.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.domain.presentation.PresentationStatus;
import com.pptagent.domain.presentation.SlideElementType;

import java.time.Instant;
import java.util.List;

public record PresentationResponse(
        Long id,
        Long ownerUserId,
        String title,
        int pageWidth,
        int pageHeight,
        JsonNode theme,
        PresentationStatus status,
        int currentVersionNumber,
        int revision,
        List<SlideResponse> slides
) {
    public PresentationResponse(Long id, Long ownerUserId, String title, int pageWidth, int pageHeight,
                                JsonNode theme, PresentationStatus status, int currentVersionNumber,
                                List<SlideResponse> slides) {
        this(id, ownerUserId, title, pageWidth, pageHeight, theme, status, currentVersionNumber, 1, slides);
    }

    // Phase 04：slides 与 elements 按 pageOrder 输出，用于标准预览和版本 JSON。
    public record SlideResponse(
            Long id,
            int pageOrder,
            String title,
            JsonNode background,
            String speakerNotes,
            int revision,
            List<ElementResponse> elements
    ) {
        public SlideResponse(Long id, int pageOrder, String title, JsonNode background,
                             String speakerNotes, List<ElementResponse> elements) {
            this(id, pageOrder, title, background, speakerNotes, 1, elements);
        }
    }

    // Phase 04：预览响应会为 IMAGE 元素的 content 补充 publicUrl。
    public record ElementResponse(
            Long id,
            String elementKey,
            SlideElementType type,
            double x,
            double y,
            double width,
            double height,
            double rotation,
            int zIndex,
            int pageOrder,
            boolean initiallyPresent,
            JsonNode content,
            JsonNode style,
            int revision,
            boolean locked
    ) {
    }

    public record VersionSummaryResponse(
            int versionNumber,
            String reason,
            Long createdByUserId,
            Instant createdAt
    ) {
    }

    // Phase 04：snapshot 是创建版本时保存的完整标准 PresentationResponse JSON。
    public record VersionResponse(
            int versionNumber,
            String reason,
            Long createdByUserId,
            Instant createdAt,
            JsonNode snapshot
    ) {
    }
}

package com.pptagent.api.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.domain.presentation.SlideElementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class PresentationRequests {

    private PresentationRequests() {
    }

    // Phase 04：请求 DTO 接收 JsonNode，标准化解析统一交给 presentation 业务服务。
    public record CreatePresentationRequest(
            @NotBlank @Size(max = 120) String title,
            Integer pageWidth,
            Integer pageHeight,
            JsonNode theme
    ) {
    }

    public record UpdatePresentationRequest(
            @NotBlank @Size(max = 120) String title,
            @NotNull Integer pageWidth,
            @NotNull Integer pageHeight,
            JsonNode theme
    ) {
    }

    public record CreateSlideRequest(
            Integer pageOrder,
            @Size(max = 120) String title,
            JsonNode background,
            String speakerNotes
    ) {
    }

    public record UpdateSlideRequest(
            Integer pageOrder,
            @Size(max = 120) String title,
            JsonNode background,
            String speakerNotes
    ) {
    }

    // Phase 04：元素 pageOrder 控制 JSON 顺序，zIndex 只控制视觉层级。
    public record CreateElementRequest(
            @NotNull SlideElementType type,
            @NotNull Double x,
            @NotNull Double y,
            @NotNull Double width,
            @NotNull Double height,
            Double rotation,
            Integer zIndex,
            Integer pageOrder,
            Boolean initiallyPresent,
            JsonNode content,
            JsonNode style
    ) {
    }

    public record UpdateElementRequest(
            @NotNull SlideElementType type,
            @NotNull Double x,
            @NotNull Double y,
            @NotNull Double width,
            @NotNull Double height,
            Double rotation,
            Integer zIndex,
            Integer pageOrder,
            Boolean initiallyPresent,
            JsonNode content,
            JsonNode style,
            @NotNull Integer expectedRevision
    ) {
    }

    // Phase 04：批量操作在同一个 service 事务内执行，失败时整体回滚。
    public record BatchElementRequest(
            @Valid @NotNull List<ElementOperation> operations
    ) {
    }

    public record ElementOperation(
            @NotBlank String action,
            Long elementId,
            SlideElementType type,
            Double x,
            Double y,
            Double width,
            Double height,
            Double rotation,
            Integer zIndex,
            Integer pageOrder,
            Boolean initiallyPresent,
            JsonNode content,
            JsonNode style,
            Integer expectedRevision
    ) {
    }

    public record ElementLockRequest(@NotNull Integer expectedRevision, @NotNull Boolean locked) {
    }

    public record DeleteElementRequest(@NotNull Integer expectedRevision) {
    }

    public record AiEditRequest(
            @NotNull List<@Valid AiElementEdit> edits,
            String command,
            String clientMutationId,
            String idempotencyKey
    ) {
        public AiEditRequest(List<AiElementEdit> edits) {
            this(edits, "POLISH_TEXT", null, null);
        }
    }

    public record AiElementEdit(
            @NotNull Long elementId,
            @NotNull Integer expectedRevision,
            JsonNode content,
            JsonNode style,
            Double x,
            Double y,
            Double width,
            Double height
    ) {
    }

    public record CreateVersionRequest(
            @Size(max = 255) String reason
    ) {
    }

    public record SharePresentationMemberRequest(
            @NotBlank @Email String email,
            @NotNull Boolean canEdit
    ) {
    }
}

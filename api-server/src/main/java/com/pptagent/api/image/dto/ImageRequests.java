package com.pptagent.api.image.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public final class ImageRequests {

    private ImageRequests() {
    }

    public record SearchCandidatesRequest(
            @NotNull String topic,
            String sourceUrl,
            Integer limit
    ) {
    }

    public record EvaluateImageRequest(
            @NotNull JsonNode candidate
    ) {
    }

    public record SaveImageAssetRequest(
            @NotNull JsonNode image
    ) {
    }
}

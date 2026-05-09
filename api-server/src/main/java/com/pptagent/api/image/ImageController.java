package com.pptagent.api.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.agent.image.AgentImageService;
import com.pptagent.api.image.dto.ImageRequests;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final AgentImageService imageService;

    public ImageController(AgentImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/search-candidates")
    public ApiResponse<ArrayNode> searchCandidates(@Valid @RequestBody ImageRequests.SearchCandidatesRequest request) {
        return ApiResponse.ok(imageService.searchCandidates(request.topic(), request.sourceUrl(), request.limit()));
    }

    @PostMapping("/evaluate")
    public ApiResponse<ObjectNode> evaluate(@Valid @RequestBody ImageRequests.EvaluateImageRequest request) {
        return ApiResponse.ok(imageService.evaluate(request.candidate()));
    }

    @PostMapping("/assets")
    public ApiResponse<ObjectNode> saveAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ImageRequests.SaveImageAssetRequest request
    ) {
        return ApiResponse.ok(imageService.saveAsset(user.id(), request.image()));
    }

    @GetMapping("/assets")
    public ApiResponse<JsonNode> listAssets(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(imageService.listAssets(user.id()));
    }
}

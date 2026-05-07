package com.pptagent.api.presentation;

import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ElementController {

    private final ElementService elementService;

    public ElementController(ElementService elementService) {
        this.elementService = elementService;
    }

    @PostMapping("/slides/{slideId}/elements")
    public ApiResponse<PresentationResponse.ElementResponse> createElement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long slideId,
            @Valid @RequestBody PresentationRequests.CreateElementRequest request
    ) {
        return ApiResponse.ok(elementService.createElement(user.id(), slideId, request));
    }

    @PatchMapping("/elements/{elementId}")
    public ApiResponse<PresentationResponse.ElementResponse> updateElement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long elementId,
            @Valid @RequestBody PresentationRequests.UpdateElementRequest request
    ) {
        return ApiResponse.ok(elementService.updateElement(user.id(), elementId, request));
    }

    @DeleteMapping("/elements/{elementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteElement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long elementId
    ) {
        elementService.deleteElement(user.id(), elementId);
    }

    @PatchMapping("/slides/{slideId}/elements/batch")
    public ApiResponse<PresentationResponse.SlideResponse> batchElements(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long slideId,
            @Valid @RequestBody PresentationRequests.BatchElementRequest request
    ) {
        return ApiResponse.ok(elementService.batchElements(user.id(), slideId, request));
    }
}

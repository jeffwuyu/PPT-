package com.pptagent.api.presentation;

import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SlideController {

    private final SlideService slideService;

    public SlideController(SlideService slideService) {
        this.slideService = slideService;
    }

    @PostMapping("/presentations/{id}/slides")
    public ApiResponse<PresentationResponse> createSlide(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody PresentationRequests.CreateSlideRequest request
    ) {
        return ApiResponse.ok(slideService.createSlide(user.id(), id, request));
    }

    @PatchMapping("/slides/{slideId}")
    public ApiResponse<PresentationResponse> updateSlide(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long slideId,
            @Valid @RequestBody PresentationRequests.UpdateSlideRequest request
    ) {
        return ApiResponse.ok(slideService.updateSlide(user.id(), slideId, request));
    }

    @DeleteMapping("/slides/{slideId}")
    public ApiResponse<PresentationResponse> deleteSlide(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long slideId
    ) {
        return ApiResponse.ok(slideService.deleteSlide(user.id(), slideId));
    }
}

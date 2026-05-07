package com.pptagent.api.presentation;

import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PresentationController {

    private final PresentationService presentationService;

    public PresentationController(PresentationService presentationService) {
        this.presentationService = presentationService;
    }

    @PostMapping("/presentations")
    public ApiResponse<PresentationResponse> createPresentation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PresentationRequests.CreatePresentationRequest request
    ) {
        return ApiResponse.ok(presentationService.createPresentation(user.id(), request));
    }

    @GetMapping("/presentations/{id}")
    public ApiResponse<PresentationResponse> getPresentation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(presentationService.getPresentation(user.id(), id));
    }

    @PatchMapping("/presentations/{id}")
    public ApiResponse<PresentationResponse> updatePresentation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody PresentationRequests.UpdatePresentationRequest request
    ) {
        return ApiResponse.ok(presentationService.updatePresentation(user.id(), id, request));
    }

    @DeleteMapping("/presentations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePresentation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        presentationService.deletePresentation(user.id(), id);
    }

    @GetMapping("/presentations/{id}/preview")
    public ApiResponse<PresentationResponse> preview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(presentationService.preview(user.id(), id));
    }

    @PostMapping("/presentations/{id}/versions")
    public ApiResponse<PresentationResponse.VersionResponse> createVersion(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody PresentationRequests.CreateVersionRequest request
    ) {
        return ApiResponse.ok(presentationService.createVersion(user.id(), id, request));
    }

    @GetMapping("/presentations/{id}/versions")
    public ApiResponse<List<PresentationResponse.VersionSummaryResponse>> listVersions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(presentationService.listVersions(user.id(), id));
    }

    @GetMapping("/presentations/{id}/versions/{versionNumber}")
    public ApiResponse<PresentationResponse.VersionResponse> getVersion(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @PathVariable int versionNumber
    ) {
        return ApiResponse.ok(presentationService.getVersion(user.id(), id, versionNumber));
    }
}

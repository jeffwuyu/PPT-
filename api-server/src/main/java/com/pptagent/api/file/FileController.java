package com.pptagent.api.file;

import com.pptagent.api.file.dto.CompleteUploadRequest;
import com.pptagent.api.file.dto.FileAssetResponse;
import com.pptagent.api.file.dto.UploadTokenRequest;
import com.pptagent.api.file.dto.UploadTokenResponse;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import com.pptagent.domain.file.FileType;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload-token")
    public ApiResponse<UploadTokenResponse> createUploadToken(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UploadTokenRequest request
    ) {
        return ApiResponse.ok(fileService.createUploadToken(user.id(), request));
    }

    @PostMapping("/complete")
    public ApiResponse<FileAssetResponse> completeUpload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CompleteUploadRequest request
    ) {
        return ApiResponse.ok(fileService.completeUpload(user.id(), request.fileId()));
    }

    @PostMapping("/upload")
    public ApiResponse<FileAssetResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam FileType fileType,
            @RequestPart MultipartFile file
    ) {
        return ApiResponse.ok(fileService.uploadFromServer(user.id(), fileType, file));
    }

    @GetMapping("/{id}")
    public ApiResponse<FileAssetResponse> getFile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(fileService.getFile(user.id(), id));
    }
}

package com.pptagent.api.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.FileType;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.domain.presentation.SlideElementType;
import org.springframework.stereotype.Service;

@Service
public class ElementValidationService {

    private final FileAssetRepository fileAssetRepository;
    private final PresentationJsonService jsonService;

    public ElementValidationService(FileAssetRepository fileAssetRepository, PresentationJsonService jsonService) {
        this.fileAssetRepository = fileAssetRepository;
        this.jsonService = jsonService;
    }

    public void validateElement(Long userId, SlideElementType type, Double width, Double height, JsonNode content) {
        if (type == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Element type is required");
        }
        jsonService.positiveDouble(width, "width");
        jsonService.positiveDouble(height, "height");

        JsonNode safeContent = jsonService.defaultObject(content);
        if (type == SlideElementType.TEXT && (!safeContent.hasNonNull("text") || safeContent.get("text").asText().isBlank())) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "TEXT element requires content.text");
        }
        if (type == SlideElementType.IMAGE && safeContent.hasNonNull("fileAssetId")) {
            validateImageAsset(userId, safeContent.get("fileAssetId").asLong());
        }
    }

    private void validateImageAsset(Long userId, Long fileAssetId) {
        FileAssetEntity asset = fileAssetRepository.findWithOwnerById(fileAssetId)
                .orElseThrow(() -> new PlatformException(ErrorCode.BAD_REQUEST, "Image file not found"));
        if (!asset.getOwnerUserId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Image file does not belong to current user");
        }
        if (asset.getStatus() != FileAssetStatus.AVAILABLE || !isImageFileType(asset.getFileType())) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Image file is not available");
        }
    }

    private boolean isImageFileType(FileType fileType) {
        return fileType == FileType.USER_IMAGE
                || fileType == FileType.CRAWLED_IMAGE
                || fileType == FileType.COVER_IMAGE
                || fileType == FileType.SLIDE_THUMBNAIL;
    }
}

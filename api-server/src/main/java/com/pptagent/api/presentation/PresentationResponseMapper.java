package com.pptagent.api.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.PresentationVersionEntity;
import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideElementType;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PresentationResponseMapper {

    private final SlideRepository slideRepository;
    private final SlideElementRepository slideElementRepository;
    private final FileAssetRepository fileAssetRepository;
    private final PresentationJsonService jsonService;

    public PresentationResponseMapper(
            SlideRepository slideRepository,
            SlideElementRepository slideElementRepository,
            FileAssetRepository fileAssetRepository,
            PresentationJsonService jsonService
    ) {
        this.slideRepository = slideRepository;
        this.slideElementRepository = slideElementRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.jsonService = jsonService;
    }

    public PresentationResponse buildPresentationResponse(PresentationEntity presentation, boolean preview) {
        List<PresentationResponse.SlideResponse> slides = slideRepository.findByPresentationIdOrderByPageOrderAsc(presentation.getId()).stream()
                .map(slide -> toSlideResponse(slide, preview))
                .toList();
        return new PresentationResponse(
                presentation.getId(),
                presentation.getOwnerUserId(),
                presentation.getTitle(),
                presentation.getPageWidth(),
                presentation.getPageHeight(),
                jsonService.fromJson(presentation.getThemeJson()),
                presentation.getStatus(),
                presentation.getCurrentVersionNumber(),
                slides
        );
    }

    public PresentationResponse.SlideResponse toSlideResponse(SlideEntity slide, boolean preview) {
        List<PresentationResponse.ElementResponse> elements = slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()).stream()
                .map(element -> toElementResponse(element, preview))
                .toList();
        return new PresentationResponse.SlideResponse(
                slide.getId(),
                slide.getPageOrder(),
                slide.getTitle(),
                jsonService.fromJson(slide.getBackgroundJson()),
                slide.getSpeakerNotes(),
                elements
        );
    }

    public PresentationResponse.ElementResponse toElementResponse(SlideElementEntity element, boolean preview) {
        return new PresentationResponse.ElementResponse(
                element.getId(),
                element.getElementKey(),
                element.getElementType(),
                element.getXPosition(),
                element.getYPosition(),
                element.getWidthValue(),
                element.getHeightValue(),
                element.getRotation(),
                element.getZIndex(),
                element.getPageOrder(),
                element.isInitiallyPresent(),
                preview ? enrichImageContent(element) : jsonService.fromJson(element.getContentJson()),
                jsonService.fromJson(element.getStyleJson())
        );
    }

    public PresentationResponse.VersionResponse toVersionResponse(PresentationVersionEntity version) {
        return new PresentationResponse.VersionResponse(
                version.getVersionNumber(),
                version.getReason(),
                version.getCreatedByUserId(),
                version.getCreatedAt(),
                jsonService.fromJson(version.getSnapshotJson())
        );
    }

    private JsonNode enrichImageContent(SlideElementEntity element) {
        JsonNode content = jsonService.fromJson(element.getContentJson());
        if (element.getElementType() != SlideElementType.IMAGE || !content.hasNonNull("fileAssetId") || !content.isObject()) {
            return content;
        }
        ObjectNode enriched = content.deepCopy();
        fileAssetRepository.findWithOwnerById(content.get("fileAssetId").asLong())
                .filter(asset -> asset.getStatus() == FileAssetStatus.AVAILABLE)
                .map(FileAssetEntity::getPublicUrl)
                .ifPresent(publicUrl -> enriched.put("publicUrl", publicUrl));
        return enriched;
    }
}

package com.pptagent.api.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.api.presentation.dto.PresentationExportResponse;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileType;
import com.pptagent.domain.file.FileUploadMode;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.PresentationExportEntity;
import com.pptagent.domain.presentation.PresentationVersionEntity;
import com.pptagent.domain.presentation.repository.PresentationExportRepository;
import com.pptagent.domain.presentation.repository.PresentationRepository;
import com.pptagent.domain.presentation.repository.PresentationVersionRepository;
import com.pptagent.infrastructure.storage.ObjectStorageClient;
import com.pptagent.infrastructure.storage.ObjectStorageException;
import com.pptagent.infrastructure.storage.StoredObject;
import com.pptagent.renderer.PptImageData;
import com.pptagent.renderer.PptRenderResult;
import com.pptagent.renderer.PptRendererService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PresentationExportService {

    private final PresentationRepository presentationRepository;
    private final PresentationVersionRepository presentationVersionRepository;
    private final PresentationExportRepository presentationExportRepository;
    private final FileAssetRepository fileAssetRepository;
    private final PresentationAccessService accessService;
    private final PresentationResponseMapper responseMapper;
    private final PptRendererService rendererService;
    private final ObjectStorageClient objectStorageClient;
    private final ObjectMapper objectMapper;

    public PresentationExportService(
            PresentationRepository presentationRepository,
            PresentationVersionRepository presentationVersionRepository,
            PresentationExportRepository presentationExportRepository,
            FileAssetRepository fileAssetRepository,
            PresentationAccessService accessService,
            PresentationResponseMapper responseMapper,
            PptRendererService rendererService,
            ObjectStorageClient objectStorageClient,
            ObjectMapper objectMapper
    ) {
        this.presentationRepository = presentationRepository;
        this.presentationVersionRepository = presentationVersionRepository;
        this.presentationExportRepository = presentationExportRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.accessService = accessService;
        this.responseMapper = responseMapper;
        this.rendererService = rendererService;
        this.objectStorageClient = objectStorageClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public PresentationExportResponse exportPresentation(Long userId, Long presentationId) {
        UserEntity user = accessService.loadUser(userId);
        PresentationEntity presentation = accessService.loadPresentationForEdit(userId, presentationId);
        int versionNumber = presentation.nextVersionNumber();
        presentationRepository.updateById(presentation);

        JsonNode snapshot = objectMapper.valueToTree(responseMapper.buildPresentationResponse(presentation, true));
        PresentationVersionEntity version = new PresentationVersionEntity(
                presentation,
                versionNumber,
                "Phase 09 export",
                snapshot.toString(),
                user
        );
        presentationVersionRepository.insert(version);

        PresentationExportEntity export = new PresentationExportEntity(presentation, versionNumber, userId);
        presentationExportRepository.insert(export);

        try {
            PptRenderResult renderResult = rendererService.render(snapshot, this::resolveImage);
            String prefix = exportPrefix(userId, presentationId, versionNumber, export.getId());
            FileAssetEntity pptx = uploadAsset(
                    user,
                    FileType.GENERATED_PPTX,
                    "presentation-%d-v%d.pptx".formatted(presentationId, versionNumber),
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    prefix + "/presentation.pptx",
                    renderResult.pptx()
            );

            List<Long> thumbnailIds = new ArrayList<>();
            List<String> thumbnailUrls = new ArrayList<>();
            Long coverFileId = null;
            String coverUrl = null;
            for (PptRenderResult.RenderedSlide slide : renderResult.slides()) {
                FileAssetEntity thumbnail = uploadAsset(
                        user,
                        slide.pageOrder() == 1 ? FileType.COVER_IMAGE : FileType.SLIDE_THUMBNAIL,
                        "slide-%02d.png".formatted(slide.pageOrder()),
                        "image/png",
                        prefix + "/slide-%02d.png".formatted(slide.pageOrder()),
                        slide.thumbnailPng()
                );
                thumbnailIds.add(thumbnail.getId());
                thumbnailUrls.add(thumbnail.getPublicUrl());
                if (coverFileId == null) {
                    coverFileId = thumbnail.getId();
                    coverUrl = thumbnail.getPublicUrl();
                }
            }

            export.markAvailable(pptx.getId(), coverFileId, objectMapper.writeValueAsString(thumbnailIds));
            presentationExportRepository.updateById(export);
            presentation.markPublished();
            presentationRepository.updateById(presentation);

            return new PresentationExportResponse(
                    export.getId(),
                    presentationId,
                    versionNumber,
                    export.getStatus(),
                    pptx.getId(),
                    coverFileId,
                    List.copyOf(thumbnailIds),
                    pptx.getPublicUrl(),
                    coverUrl,
                    List.copyOf(thumbnailUrls),
                    null
            );
        } catch (PlatformException exception) {
            export.markFailed(exception.getMessage());
            presentationExportRepository.updateById(export);
            throw exception;
        } catch (Exception exception) {
            export.markFailed(exception.getMessage());
            presentationExportRepository.updateById(export);
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Presentation export failed");
        }
    }

    private Optional<PptImageData> resolveImage(Long fileAssetId) {
        FileAssetEntity asset = fileAssetRepository.selectById(fileAssetId);
        if (asset == null || asset.getPublicUrl() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PptImageData(objectStorageClient.getObjectBytes(asset.getObjectKey()), asset.getContentType()));
        } catch (ObjectStorageException exception) {
            return Optional.empty();
        }
    }

    private FileAssetEntity uploadAsset(
            UserEntity user,
            FileType fileType,
            String filename,
            String contentType,
            String objectKey,
            byte[] content
    ) {
        FileAssetEntity asset = new FileAssetEntity(
                user,
                fileType,
                filename,
                contentType,
                content.length,
                objectKey,
                FileUploadMode.SERVER
        );
        fileAssetRepository.insert(asset);
        try {
            StoredObject storedObject = objectStorageClient.putObject(objectKey, contentType, content);
            asset.markAvailable(storedObject.publicUrl());
            fileAssetRepository.updateById(asset);
            return asset;
        } catch (ObjectStorageException exception) {
            asset.markFailed(exception.getMessage());
            fileAssetRepository.updateById(asset);
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Export artifact upload failed");
        }
    }

    private String exportPrefix(Long userId, Long presentationId, int versionNumber, Long exportId) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return "users/%d/exports/%04d/%02d/%02d/presentations/%d/v%d/%s".formatted(
                userId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                presentationId,
                versionNumber,
                UUID.randomUUID() + "-" + exportId
        ).toLowerCase(Locale.ROOT);
    }
}

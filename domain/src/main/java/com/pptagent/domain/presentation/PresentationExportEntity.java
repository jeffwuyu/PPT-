package com.pptagent.domain.presentation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("presentation_exports")
public class PresentationExportEntity {

    @TableId
    private Long id;
    private Long presentationId;
    private int versionNumber;
    private PresentationExportStatus status;
    private Long pptxFileId;
    private Long coverFileId;
    private String thumbnailFileIdsJson;
    private Long createdByUserId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected PresentationExportEntity() {
    }

    public PresentationExportEntity(PresentationEntity presentation, int versionNumber, Long createdByUserId) {
        this.presentationId = presentation.getId();
        this.versionNumber = versionNumber;
        this.createdByUserId = createdByUserId;
        this.status = PresentationExportStatus.PENDING;
        this.thumbnailFileIdsJson = "[]";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public PresentationExportStatus getStatus() {
        return status;
    }

    public Long getPptxFileId() {
        return pptxFileId;
    }

    public Long getCoverFileId() {
        return coverFileId;
    }

    public String getThumbnailFileIdsJson() {
        return thumbnailFileIdsJson;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markAvailable(Long pptxFileId, Long coverFileId, String thumbnailFileIdsJson) {
        this.pptxFileId = pptxFileId;
        this.coverFileId = coverFileId;
        this.thumbnailFileIdsJson = thumbnailFileIdsJson;
        this.status = PresentationExportStatus.AVAILABLE;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String failureReason) {
        this.status = PresentationExportStatus.FAILED;
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }
}

package com.pptagent.domain.presentation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pptagent.domain.auth.UserEntity;

import java.time.Instant;

@TableName("presentation_versions")
public class PresentationVersionEntity {

    @TableId
    private Long id;
    private Long presentationId;
    private int versionNumber;
    private String reason;
    // Phase 04：该版本对应的不可变标准 Presentation JSON 快照。
    private String snapshotJson;
    private Long createdByUserId;
    private Instant createdAt;

    @TableField(exist = false)
    private PresentationEntity presentation;

    @TableField(exist = false)
    private UserEntity createdBy;

    protected PresentationVersionEntity() {
    }

    public PresentationVersionEntity(
            PresentationEntity presentation,
            int versionNumber,
            String reason,
            String snapshotJson,
            UserEntity createdBy
    ) {
        this.presentation = presentation;
        this.presentationId = presentation.getId();
        this.versionNumber = versionNumber;
        this.reason = reason;
        this.snapshotJson = snapshotJson;
        this.createdBy = createdBy;
        this.createdByUserId = createdBy.getId();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public PresentationEntity getPresentation() {
        return presentation;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getReason() {
        return reason;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setPresentation(PresentationEntity presentation) {
        this.presentation = presentation;
        this.presentationId = presentation == null ? null : presentation.getId();
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
        this.createdByUserId = createdBy == null ? null : createdBy.getId();
    }
}

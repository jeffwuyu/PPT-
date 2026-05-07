package com.pptagent.domain.file;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pptagent.domain.auth.UserEntity;

import java.time.Instant;

@TableName("file_assets")
public class FileAssetEntity {

    @TableId
    private Long id;
    private Long ownerUserId;
    private FileType fileType;
    private String originalFilename;
    private String contentType;
    private long byteSize;
    private String objectKey;
    private String publicUrl;
    private FileAssetStatus status;
    private FileUploadMode uploadMode;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    @TableField(exist = false)
    private UserEntity owner;

    protected FileAssetEntity() {
    }

    public FileAssetEntity(
            UserEntity owner,
            FileType fileType,
            String originalFilename,
            String contentType,
            long byteSize,
            String objectKey,
            FileUploadMode uploadMode
    ) {
        this.owner = owner;
        this.ownerUserId = owner.getId();
        this.fileType = fileType;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.objectKey = objectKey;
        this.uploadMode = uploadMode;
        this.status = FileAssetStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public FileAssetStatus getStatus() {
        return status;
    }

    public FileUploadMode getUploadMode() {
        return uploadMode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
        this.ownerUserId = owner == null ? null : owner.getId();
    }

    public void markAvailable(String publicUrl) {
        this.publicUrl = publicUrl;
        this.status = FileAssetStatus.AVAILABLE;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = FileAssetStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }
}

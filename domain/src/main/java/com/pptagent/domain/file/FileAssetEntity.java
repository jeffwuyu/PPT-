package com.pptagent.domain.file;

import com.pptagent.domain.auth.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "file_assets")
public class FileAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FileType fileType;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long byteSize;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(length = 1000)
    private String publicUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileAssetStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileUploadMode uploadMode;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

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

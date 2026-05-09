package com.pptagent.domain.presentation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pptagent.domain.auth.UserEntity;

import java.time.Instant;

@TableName("presentations")
public class PresentationEntity {

    @TableId
    private Long id;
    private Long ownerUserId;
    private String title;
    private int pageWidth;
    private int pageHeight;
    // Phase 04：themeJson 以文本形式保存演示文稿级 JSON 数据。
    private String themeJson;
    private PresentationStatus status;
    // Phase 04：写入 PresentationVersion 快照前递增版本号。
    private int currentVersionNumber;
    private Instant createdAt;
    private Instant updatedAt;
    private String isDeleted;

    @TableField(exist = false)
    private UserEntity owner;

    protected PresentationEntity() {
    }

    public PresentationEntity(UserEntity owner, String title, int pageWidth, int pageHeight, String themeJson) {
        this.owner = owner;
        this.ownerUserId = owner.getId();
        this.title = title;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.themeJson = themeJson;
        this.status = PresentationStatus.ACTIVE;
        this.currentVersionNumber = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.isDeleted = "0";
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

    public String getTitle() {
        return title;
    }

    public int getPageWidth() {
        return pageWidth;
    }

    public int getPageHeight() {
        return pageHeight;
    }

    public String getThemeJson() {
        return themeJson;
    }

    public PresentationStatus getStatus() {
        return status;
    }

    public int getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getIsDeleted() {
        return isDeleted;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
        this.ownerUserId = owner == null ? null : owner.getId();
    }

    public void update(String title, int pageWidth, int pageHeight, String themeJson) {
        this.title = title;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.themeJson = themeJson;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.status = PresentationStatus.DELETED;
        this.isDeleted = "1";
        this.updatedAt = Instant.now();
    }

    public void markPublished() {
        this.status = PresentationStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public int nextVersionNumber() {
        this.currentVersionNumber = this.currentVersionNumber + 1;
        this.updatedAt = Instant.now();
        return this.currentVersionNumber;
    }
}

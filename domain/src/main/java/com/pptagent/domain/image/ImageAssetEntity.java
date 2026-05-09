package com.pptagent.domain.image;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("image_assets")
public class ImageAssetEntity {

    @TableId
    private Long id;
    private Long ownerUserId;
    private ImageSourceType sourceType;
    private String sourceUrl;
    private String publicUrl;
    private String licenseNote;
    private int width;
    private int height;
    private String imageFormat;
    private String tagsJson;
    private String descriptionText;
    private String sceneText;
    private double qualityScore;
    private ImageSuitabilityStatus suitabilityStatus;
    private boolean suitableForPpt;
    private String metadataJson;
    private Instant createdAt;
    private Instant updatedAt;

    protected ImageAssetEntity() {
    }

    public ImageAssetEntity(
            Long ownerUserId,
            ImageSourceType sourceType,
            String sourceUrl,
            String publicUrl,
            String licenseNote,
            int width,
            int height,
            String imageFormat,
            String tagsJson,
            String descriptionText,
            String sceneText,
            double qualityScore,
            ImageSuitabilityStatus suitabilityStatus,
            boolean suitableForPpt,
            String metadataJson
    ) {
        this.ownerUserId = ownerUserId;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.publicUrl = publicUrl;
        this.licenseNote = licenseNote;
        this.width = width;
        this.height = height;
        this.imageFormat = imageFormat;
        this.tagsJson = tagsJson;
        this.descriptionText = descriptionText;
        this.sceneText = sceneText;
        this.qualityScore = qualityScore;
        this.suitabilityStatus = suitabilityStatus;
        this.suitableForPpt = suitableForPpt;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public ImageSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getLicenseNote() {
        return licenseNote;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getImageFormat() {
        return imageFormat;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public String getSceneText() {
        return sceneText;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public ImageSuitabilityStatus getSuitabilityStatus() {
        return suitabilityStatus;
    }

    public boolean isSuitableForPpt() {
        return suitableForPpt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}

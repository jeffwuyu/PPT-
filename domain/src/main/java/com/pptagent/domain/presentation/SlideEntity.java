package com.pptagent.domain.presentation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("presentation_slides")
public class SlideEntity {

    @TableId
    private Long id;
    private Long presentationId;
    // Phase 04：pageOrder 是导出标准 JSON 中从 1 开始的页面顺序。
    private int pageOrder;
    private String title;
    // Phase 04：backgroundJson 以文本形式保存页面级背景 JSON。
    private String backgroundJson;
    private String speakerNotes;
    private Instant createdAt;
    private Instant updatedAt;
    private String isDeleted;
    private int revision = 1;

    @TableField(exist = false)
    private PresentationEntity presentation;

    protected SlideEntity() {
    }

    public SlideEntity(PresentationEntity presentation, int pageOrder, String title, String backgroundJson, String speakerNotes) {
        this.presentation = presentation;
        this.presentationId = presentation.getId();
        this.pageOrder = pageOrder;
        this.title = title;
        this.backgroundJson = backgroundJson;
        this.speakerNotes = speakerNotes;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.isDeleted = "0";
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

    public int getPageOrder() {
        return pageOrder;
    }

    public String getTitle() {
        return title;
    }

    public String getBackgroundJson() {
        return backgroundJson;
    }

    public String getSpeakerNotes() {
        return speakerNotes;
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

    public int getRevision() { return revision; }
    public boolean matchesRevision(int expected) { return revision == expected; }
    public void incrementRevision() { revision++; this.updatedAt = Instant.now(); }

    public void setPresentation(PresentationEntity presentation) {
        this.presentation = presentation;
        this.presentationId = presentation == null ? null : presentation.getId();
    }

    public void update(String title, String backgroundJson, String speakerNotes) {
        this.title = title;
        this.backgroundJson = backgroundJson;
        this.speakerNotes = speakerNotes;
        this.updatedAt = Instant.now();
    }

    public void changePageOrder(int pageOrder) {
        this.pageOrder = pageOrder;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.isDeleted = "1";
        this.updatedAt = Instant.now();
    }
}

package com.pptagent.domain.presentation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("slide_elements")
public class SlideElementEntity {

    @TableId
    private Long id;
    private Long slideId;
    private String elementKey;
    private SlideElementType elementType;
    private double xPosition;
    private double yPosition;
    private double widthValue;
    private double heightValue;
    private double rotation;
    // Phase 04：zIndex 表示视觉层级，修改它不应改变 JSON 结构顺序。
    private int zIndex;
    // Phase 04：pageOrder 是页面内元素在标准 JSON 中从 1 开始的顺序。
    private int pageOrder;
    // Phase 04：标记该元素是否来自页面或模板初始化。
    private boolean initiallyPresent;
    // Phase 04：元素 content/style JSON 以文本保存，由 service 层解析。
    private String contentJson;
    private String styleJson;
    private Instant createdAt;
    private Instant updatedAt;
    private String isDeleted;
    /** Optimistic concurrency version for human and AI edits. */
    private int revision = 1;
    /** Persistent human protection flag; AI writes must never clear it. */
    private boolean locked;

    @TableField(exist = false)
    private SlideEntity slide;

    protected SlideElementEntity() {
    }

    public SlideElementEntity(
            SlideEntity slide,
            String elementKey,
            SlideElementType elementType,
            double xPosition,
            double yPosition,
            double widthValue,
            double heightValue,
            double rotation,
            int zIndex,
            int pageOrder,
            boolean initiallyPresent,
            String contentJson,
            String styleJson
    ) {
        this.slide = slide;
        this.slideId = slide.getId();
        this.elementKey = elementKey;
        this.elementType = elementType;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.widthValue = widthValue;
        this.heightValue = heightValue;
        this.rotation = rotation;
        this.zIndex = zIndex;
        this.pageOrder = pageOrder;
        this.initiallyPresent = initiallyPresent;
        this.contentJson = contentJson;
        this.styleJson = styleJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.isDeleted = "0";
    }

    public Long getId() {
        return id;
    }

    public Long getSlideId() {
        return slideId;
    }

    public SlideEntity getSlide() {
        return slide;
    }

    public String getElementKey() {
        return elementKey;
    }

    public SlideElementType getElementType() {
        return elementType;
    }

    public double getXPosition() {
        return xPosition;
    }

    public double getYPosition() {
        return yPosition;
    }

    public double getWidthValue() {
        return widthValue;
    }

    public double getHeightValue() {
        return heightValue;
    }

    public double getRotation() {
        return rotation;
    }

    public int getZIndex() {
        return zIndex;
    }

    public int getPageOrder() {
        return pageOrder;
    }

    public boolean isInitiallyPresent() {
        return initiallyPresent;
    }

    public String getContentJson() {
        return contentJson;
    }

    public String getStyleJson() {
        return styleJson;
    }

    public String getIsDeleted() {
        return isDeleted;
    }

    public int getRevision() {
        return revision;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        this.updatedAt = Instant.now();
    }

    public boolean matchesRevision(int expectedRevision) {
        return revision == expectedRevision;
    }

    public void incrementRevision() {
        this.revision++;
        this.updatedAt = Instant.now();
    }

    public void setSlide(SlideEntity slide) {
        this.slide = slide;
        this.slideId = slide == null ? null : slide.getId();
    }

    public void update(
            SlideElementType elementType,
            double xPosition,
            double yPosition,
            double widthValue,
            double heightValue,
            double rotation,
            int zIndex,
            int pageOrder,
            boolean initiallyPresent,
            String contentJson,
            String styleJson
    ) {
        this.elementType = elementType;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.widthValue = widthValue;
        this.heightValue = heightValue;
        this.rotation = rotation;
        this.zIndex = zIndex;
        this.pageOrder = pageOrder;
        this.initiallyPresent = initiallyPresent;
        this.contentJson = contentJson;
        this.styleJson = styleJson;
        this.revision++;
        this.updatedAt = Instant.now();
    }

    public void changePageOrder(int pageOrder) {
        this.pageOrder = pageOrder;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.isDeleted = "1";
        this.revision++;
        this.updatedAt = Instant.now();
    }
}

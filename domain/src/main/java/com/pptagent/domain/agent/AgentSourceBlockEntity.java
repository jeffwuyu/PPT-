package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_source_blocks")
public class AgentSourceBlockEntity {
    @TableId
    private Long id;
    private Long parseJobId;
    private String blockId;
    private String blockType;
    private String text;
    private String locatorJson;
    private Instant createdAt;

    protected AgentSourceBlockEntity() { }

    public AgentSourceBlockEntity(Long parseJobId, String blockId, String blockType, String text, String locatorJson) {
        this.parseJobId = parseJobId;
        this.blockId = blockId;
        this.blockType = blockType;
        this.text = text;
        this.locatorJson = locatorJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getParseJobId() { return parseJobId; }
    public String getBlockId() { return blockId; }
    public String getBlockType() { return blockType; }
    public String getText() { return text; }
    public String getLocatorJson() { return locatorJson; }
    public Instant getCreatedAt() { return createdAt; }
}

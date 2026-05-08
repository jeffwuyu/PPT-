package com.pptagent.domain.collaboration;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("collaboration_snapshots")
/**
 * 协同回放快照。
 *
 * <p>快照记录某个 presentation 到 `snapshotUpdateId` 为止的可回放状态。
 * 当前实现保存 compact JSON，不做 Java 端 Yjs merge。</p>
 */
public class CollaborationSnapshotEntity {

    @TableId
    private Long id;
    private Long presentationId;
    private Long snapshotUpdateId;
    private String snapshotJson;
    private int updateCount;
    private Instant createdAt;

    protected CollaborationSnapshotEntity() {
    }

    public CollaborationSnapshotEntity(Long presentationId, Long snapshotUpdateId, String snapshotJson, int updateCount) {
        this.presentationId = presentationId;
        this.snapshotUpdateId = snapshotUpdateId;
        this.snapshotJson = snapshotJson;
        this.updateCount = updateCount;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public Long getSnapshotUpdateId() {
        return snapshotUpdateId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public int getUpdateCount() {
        return updateCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

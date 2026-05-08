package com.pptagent.domain.collaboration;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("collaboration_locks")
/**
 * 协同软锁实体。
 *
 * <p>软锁用于保护删除、冻结导出、批量替换等高风险操作。普通协同编辑
 * 不需要锁，仍交给 CRDT/Yjs 自行解决并发合并。</p>
 */
public class CollaborationLockEntity {

    @TableId
    private Long id;
    private Long presentationId;
    private CollaborationLockTargetType targetType;
    private Long targetId;
    private Long ownerUserId;
    private String clientId;
    private String lockToken;
    private String operation;
    private Instant expiresAt;
    private Instant releasedAt;
    private Instant createdAt;

    protected CollaborationLockEntity() {
    }

    public CollaborationLockEntity(
            Long presentationId,
            CollaborationLockTargetType targetType,
            Long targetId,
            Long ownerUserId,
            String clientId,
            String lockToken,
            String operation,
            Instant expiresAt
    ) {
        this.presentationId = presentationId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ownerUserId = ownerUserId;
        this.clientId = clientId;
        this.lockToken = lockToken;
        this.operation = operation;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public CollaborationLockTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getLockToken() {
        return lockToken;
    }

    public String getOperation() {
        return operation;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActiveAt(Instant now) {
        return releasedAt == null && expiresAt.isAfter(now);
    }

    public void release() {
        this.releasedAt = Instant.now();
    }
}

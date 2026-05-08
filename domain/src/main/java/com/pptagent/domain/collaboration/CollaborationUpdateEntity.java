package com.pptagent.domain.collaboration;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("collaboration_updates")
/**
 * 协同增量记录。
 *
 * <p>`id` 使用数据库自增值，并作为 WebSocket 协议里的 updateId。
 * `payloadBase64` 保存客户端 Yjs update 原文，服务端只负责排序、存储和回放。</p>
 */
public class CollaborationUpdateEntity {

    @TableId
    private Long id;
    private Long presentationId;
    private Long actorUserId;
    private String clientId;
    private String payloadBase64;
    private CollaborationUpdateType messageType;
    private Instant createdAt;

    protected CollaborationUpdateEntity() {
    }

    public CollaborationUpdateEntity(Long presentationId, Long actorUserId, String clientId, String payloadBase64) {
        this.presentationId = presentationId;
        this.actorUserId = actorUserId;
        this.clientId = clientId;
        this.payloadBase64 = payloadBase64;
        this.messageType = CollaborationUpdateType.YJS_UPDATE;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getPayloadBase64() {
        return payloadBase64;
    }

    public CollaborationUpdateType getMessageType() {
        return messageType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

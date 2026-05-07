package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("project_members")
public class ProjectMemberEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Long userId;
    private ProjectRole projectRole;
    private boolean canEdit;
    private Instant createdAt;

    @TableField(exist = false)
    private UserEntity user;

    protected ProjectMemberEntity() {
    }

    public ProjectMemberEntity(Long projectId, UserEntity user, ProjectRole projectRole, boolean canEdit) {
        this.projectId = projectId;
        this.user = user;
        this.userId = user.getId();
        this.projectRole = projectRole;
        this.canEdit = canEdit;
        this.createdAt = Instant.now();
    }

    public boolean canEdit() {
        return canEdit || projectRole == ProjectRole.OWNER || projectRole == ProjectRole.EDITOR;
    }
}

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
    private String isDeleted;

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
        this.isDeleted = "0";
    }

    public boolean canEdit() {
        return canEdit || projectRole == ProjectRole.OWNER || projectRole == ProjectRole.EDITOR;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public ProjectRole getProjectRole() {
        return projectRole;
    }

    public String getIsDeleted() {
        return isDeleted;
    }

    public void changeRole(ProjectRole projectRole, boolean canEdit) {
        this.projectRole = projectRole;
        this.canEdit = canEdit;
    }

    public void restore(ProjectRole projectRole, boolean canEdit) {
        changeRole(projectRole, canEdit);
        this.isDeleted = "0";
    }

    public void markDeleted() {
        this.isDeleted = "1";
    }
}

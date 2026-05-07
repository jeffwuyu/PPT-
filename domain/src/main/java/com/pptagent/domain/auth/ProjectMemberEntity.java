package com.pptagent.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectRole projectRole;

    @Column(nullable = false)
    private boolean canEdit;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProjectMemberEntity() {
    }

    public ProjectMemberEntity(Long projectId, UserEntity user, ProjectRole projectRole, boolean canEdit) {
        this.projectId = projectId;
        this.user = user;
        this.projectRole = projectRole;
        this.canEdit = canEdit;
        this.createdAt = Instant.now();
    }

    public boolean canEdit() {
        return canEdit || projectRole == ProjectRole.OWNER || projectRole == ProjectRole.EDITOR;
    }
}

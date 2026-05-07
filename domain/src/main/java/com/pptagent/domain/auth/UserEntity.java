package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@TableName("users")
public class UserEntity {

    @TableId
    private Long id;
    private String email;
    private String username;
    private String passwordHash;
    private Long levelId;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    @TableField(exist = false)
    private UserLevelEntity level;

    @TableField(exist = false)
    private Set<RoleEntity> roles = new HashSet<>();

    protected UserEntity() {
    }

    public UserEntity(String email, String username, String passwordHash, UserLevelEntity level) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        changeLevel(level);
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Long getLevelId() {
        return levelId;
    }

    public UserLevelEntity getLevel() {
        return level;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }

    public void setLevel(UserLevelEntity level) {
        this.level = level;
        this.levelId = level == null ? null : level.getId();
    }

    public void setRoles(Set<RoleEntity> roles) {
        this.roles = roles == null ? new HashSet<>() : roles;
    }

    public void addRole(RoleEntity role) {
        this.roles.add(role);
        this.updatedAt = Instant.now();
    }

    public void changeLevel(UserLevelEntity level) {
        setLevel(level);
        this.updatedAt = Instant.now();
    }
}

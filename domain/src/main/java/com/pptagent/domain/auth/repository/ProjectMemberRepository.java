package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.ProjectMemberEntity;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends BaseMapper<ProjectMemberEntity> {

    default Optional<ProjectMemberEntity> findByProjectIdAndUserId(Long projectId, Long userId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<ProjectMemberEntity>()
                .eq("project_id", projectId)
                .eq("user_id", userId)
                .eq("is_deleted", "0")));
    }

    default Optional<ProjectMemberEntity> findAnyByProjectIdAndUserId(Long projectId, Long userId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<ProjectMemberEntity>()
                .eq("project_id", projectId)
                .eq("user_id", userId)));
    }

    default List<ProjectMemberEntity> findByUserId(Long userId) {
        return selectList(new QueryWrapper<ProjectMemberEntity>()
                .eq("user_id", userId)
                .eq("is_deleted", "0")
                .orderByDesc("id"));
    }

    default ProjectMemberEntity save(ProjectMemberEntity projectMember) {
        insert(projectMember);
        return projectMember;
    }

    default int deleteByProjectIdAndUserId(Long projectId, Long userId) {
        Optional<ProjectMemberEntity> existing = findByProjectIdAndUserId(projectId, userId);
        existing.ifPresent(member -> {
            member.markDeleted();
            updateById(member);
        });
        return existing.isPresent() ? 1 : 0;
    }
}

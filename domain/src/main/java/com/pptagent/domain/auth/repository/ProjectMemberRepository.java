package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.ProjectMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(Long projectId, Long userId);
}

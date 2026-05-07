package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.RoleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByCode(String code);
}

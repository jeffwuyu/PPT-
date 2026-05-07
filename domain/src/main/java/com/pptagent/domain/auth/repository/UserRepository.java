package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = {"level", "roles", "roles.permissions"})
    Optional<UserEntity> findByEmail(String email);

    @EntityGraph(attributePaths = {"level", "roles", "roles.permissions"})
    Optional<UserEntity> findWithRolesById(Long id);
}

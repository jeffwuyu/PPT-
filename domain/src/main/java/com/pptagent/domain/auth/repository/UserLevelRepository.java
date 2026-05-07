package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.UserLevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLevelRepository extends JpaRepository<UserLevelEntity, Long> {

    Optional<UserLevelEntity> findByCode(String code);
}

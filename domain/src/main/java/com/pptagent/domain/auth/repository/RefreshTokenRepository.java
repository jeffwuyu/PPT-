package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.RefreshTokenEntity;
import com.pptagent.domain.auth.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    void deleteByUser(UserEntity user);
}

package com.pptagent.domain.file.repository;

import com.pptagent.domain.file.FileAssetEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileAssetRepository extends JpaRepository<FileAssetEntity, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<FileAssetEntity> findWithOwnerById(Long id);
}

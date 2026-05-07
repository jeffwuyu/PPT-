package com.pptagent.domain.file.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.file.FileAssetEntity;

import java.util.List;
import java.util.Optional;

public interface FileAssetRepository extends BaseMapper<FileAssetEntity> {

    default Optional<FileAssetEntity> findWithOwnerById(Long id) {
        return Optional.ofNullable(selectById(id));
    }

    default List<FileAssetEntity> findAll() {
        return selectList(new QueryWrapper<>());
    }
}

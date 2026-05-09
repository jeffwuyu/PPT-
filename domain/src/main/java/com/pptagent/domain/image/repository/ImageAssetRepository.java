package com.pptagent.domain.image.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.image.ImageAssetEntity;

import java.util.List;

public interface ImageAssetRepository extends BaseMapper<ImageAssetEntity> {

    default List<ImageAssetEntity> findByOwnerUserId(Long ownerUserId) {
        return selectList(new QueryWrapper<ImageAssetEntity>()
                .eq("owner_user_id", ownerUserId)
                .orderByDesc("id"));
    }
}

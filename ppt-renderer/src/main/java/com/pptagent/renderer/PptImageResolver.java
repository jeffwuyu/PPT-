package com.pptagent.renderer;

import java.util.Optional;

@FunctionalInterface
public interface PptImageResolver {

    Optional<PptImageData> resolve(Long fileAssetId);
}

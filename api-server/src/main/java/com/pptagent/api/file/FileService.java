package com.pptagent.api.file;

import com.pptagent.api.file.dto.FileAssetResponse;
import com.pptagent.api.file.dto.UploadTokenRequest;
import com.pptagent.api.file.dto.UploadTokenResponse;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileUploadMode;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.infrastructure.storage.ObjectStorageClient;
import com.pptagent.infrastructure.storage.ObjectStorageException;
import com.pptagent.infrastructure.storage.PresignedUpload;
import com.pptagent.infrastructure.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileService {

    private static final int MAX_UPLOAD_RETRIES = 3;

    private final FileAssetRepository fileAssetRepository;
    private final UserRepository userRepository;
    private final ObjectStorageClient objectStorageClient;
    private final FileProperties fileProperties;

    public FileService(
            FileAssetRepository fileAssetRepository,
            UserRepository userRepository,
            ObjectStorageClient objectStorageClient,
            FileProperties fileProperties
    ) {
        this.fileAssetRepository = fileAssetRepository;
        this.userRepository = userRepository;
        this.objectStorageClient = objectStorageClient;
        this.fileProperties = fileProperties;
    }

    @Transactional
    public UploadTokenResponse createUploadToken(Long userId, UploadTokenRequest request) {
        validateFile(request.fileType(), request.originalFilename(), request.contentType(), request.byteSize());
        UserEntity owner = loadUser(userId);
        String objectKey = buildObjectKey(userId, request.fileType().name(), request.originalFilename());
        FileAssetEntity asset = new FileAssetEntity(
                owner,
                request.fileType(),
                request.originalFilename(),
                request.contentType(),
                request.byteSize(),
                objectKey,
                FileUploadMode.PRESIGNED
        );
        fileAssetRepository.insert(asset);
        PresignedUpload presignedUpload = objectStorageClient.createPresignedUpload(
                objectKey,
                request.contentType(),
                fileProperties.presignedUploadTtl()
        );
        return new UploadTokenResponse(asset.getId(), objectKey, presignedUpload.uploadUrl(), presignedUpload.expiresAt());
    }

    @Transactional
    public FileAssetResponse completeUpload(Long userId, Long fileId) {
        FileAssetEntity asset = loadOwnedAsset(userId, fileId);
        asset.markAvailable(objectStorageClient.publicUrl(asset.getObjectKey()));
        fileAssetRepository.updateById(asset);
        return toResponse(asset);
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public FileAssetResponse uploadFromServer(Long userId, com.pptagent.domain.file.FileType fileType, MultipartFile file) {
        validateFile(fileType, safeOriginalFilename(file), safeContentType(file), file.getSize());
        UserEntity owner = loadUser(userId);
        String objectKey = buildObjectKey(userId, fileType.name(), safeOriginalFilename(file));
        FileAssetEntity asset = new FileAssetEntity(
                owner,
                fileType,
                safeOriginalFilename(file),
                safeContentType(file),
                file.getSize(),
                objectKey,
                FileUploadMode.SERVER
        );
        fileAssetRepository.insert(asset);

        try {
            StoredObject storedObject = uploadWithRetry(objectKey, safeContentType(file), file.getBytes());
            asset.markAvailable(storedObject.publicUrl());
            fileAssetRepository.updateById(asset);
            return toResponse(asset);
        } catch (IOException | ObjectStorageException exception) {
            asset.markFailed(exception.getMessage());
            fileAssetRepository.updateById(asset);
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "File upload failed");
        }
    }

    @Transactional(readOnly = true)
    public FileAssetResponse getFile(Long userId, Long fileId) {
        return toResponse(loadOwnedAsset(userId, fileId));
    }

    private StoredObject uploadWithRetry(String objectKey, String contentType, byte[] content) {
        ObjectStorageException lastException = null;
        for (int attempt = 1; attempt <= MAX_UPLOAD_RETRIES; attempt++) {
            try {
                return objectStorageClient.putObject(objectKey, contentType, content);
            } catch (ObjectStorageException exception) {
                lastException = exception;
            }
        }
        throw lastException == null ? new ObjectStorageException("Unknown upload failure") : lastException;
    }

    private void validateFile(com.pptagent.domain.file.FileType fileType, String originalFilename, String contentType, long byteSize) {
        if (byteSize <= 0) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "File must not be empty");
        }
        if (byteSize > fileProperties.maxUploadSize().toBytes()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "File exceeds max upload size");
        }
        String extension = extension(originalFilename);
        if (!fileType.supports(contentType.toLowerCase(Locale.ROOT), extension)) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "File type does not match content type or extension");
        }
    }

    private FileAssetEntity loadOwnedAsset(Long userId, Long fileId) {
        FileAssetEntity asset = fileAssetRepository.findWithOwnerById(fileId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "File not found"));
        if (!asset.getOwnerUserId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "File does not belong to current user");
        }
        return asset;
    }

    private UserEntity loadUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "User not found"));
    }

    private String buildObjectKey(Long userId, String fileType, String originalFilename) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return "users/%d/%s/%04d/%02d/%02d/%s-%s".formatted(
                userId,
                fileType.toLowerCase(Locale.ROOT),
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                sanitizeFilename(originalFilename)
        );
    }

    private FileAssetResponse toResponse(FileAssetEntity asset) {
        return new FileAssetResponse(
                asset.getId(),
                asset.getFileType(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getByteSize(),
                asset.getObjectKey(),
                asset.getPublicUrl(),
                asset.getStatus(),
                asset.getUploadMode(),
                asset.getFailureReason()
        );
    }

    private String safeOriginalFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename == null || filename.isBlank() ? "upload.bin" : filename;
    }

    private String safeContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

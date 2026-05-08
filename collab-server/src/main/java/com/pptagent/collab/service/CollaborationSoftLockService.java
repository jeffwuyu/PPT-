package com.pptagent.collab.service;

import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.collaboration.CollaborationLockEntity;
import com.pptagent.domain.collaboration.CollaborationLockTargetType;
import com.pptagent.domain.collaboration.repository.CollaborationLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
/**
 * 高风险协同操作的软锁服务。
 *
 * <p>软锁不是 CRDT 的替代品，只用于删除、批量替换、导出冻结等很难
 * 通过普通增量自然合并的操作。普通文本、位置、样式编辑仍由客户端
 * Yjs 自身解决并发合并。</p>
 */
public class CollaborationSoftLockService {

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(30);

    private final CollaborationLockRepository lockRepository;

    public CollaborationSoftLockService(CollaborationLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @Transactional
    public LockResult acquire(
            Long presentationId,
            CollaborationLockTargetType targetType,
            Long targetId,
            Long ownerUserId,
            String clientId,
            String lockToken,
            String operation
    ) {
        Instant now = Instant.now();
        Optional<CollaborationLockEntity> active = lockRepository.findActiveLock(presentationId, targetType, targetId, now);
        if (active.isPresent()) {
            CollaborationLockEntity lock = active.get();
            if (!lock.getOwnerUserId().equals(ownerUserId) || !lock.getClientId().equals(clientId)) {
                return LockResult.denied(lock);
            }
            /*
             * 同一 user + client 重复申请同一目标锁视为幂等成功。
             * 这样客户端重试 lock-acquire 时不会制造多把锁。
             */
            return LockResult.granted(lock);
        }
        CollaborationLockEntity lock = new CollaborationLockEntity(
                presentationId,
                targetType,
                targetId,
                ownerUserId,
                clientId,
                lockToken,
                operation,
                now.plus(DEFAULT_LOCK_TTL)
        );
        lockRepository.insert(lock);
        return LockResult.granted(lock);
    }

    @Transactional
    public CollaborationLockEntity release(String lockToken, Long ownerUserId, String clientId) {
        CollaborationLockEntity lock = lockRepository.findActiveByToken(lockToken, Instant.now())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Active collaboration lock not found"));
        if (!lock.getOwnerUserId().equals(ownerUserId) || !lock.getClientId().equals(clientId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Only lock owner can release collaboration lock");
        }
        lock.release();
        lockRepository.updateById(lock);
        return lock;
    }

    @Transactional(readOnly = true)
    public boolean validatesLockToken(
            Long presentationId,
            CollaborationLockTargetType targetType,
            Long targetId,
            String lockToken,
            Long ownerUserId,
            String clientId
    ) {
        if (targetType == null || targetId == null) {
            return true;
        }
        /*
         * 未持锁的目标允许普通 update 通过；只有目标上存在 active lock 时，
         * 才要求消息携带匹配 lockToken。
         */
        Optional<CollaborationLockEntity> active = lockRepository.findActiveLock(
                presentationId,
                targetType,
                targetId,
                Instant.now()
        );
        if (active.isEmpty()) {
            return true;
        }
        CollaborationLockEntity lock = active.get();
        return lock.getLockToken().equals(lockToken)
                && lock.getOwnerUserId().equals(ownerUserId)
                && lock.getClientId().equals(clientId);
    }

    public record LockResult(boolean granted, CollaborationLockEntity lock) {
        static LockResult granted(CollaborationLockEntity lock) {
            return new LockResult(true, lock);
        }

        static LockResult denied(CollaborationLockEntity lock) {
            return new LockResult(false, lock);
        }
    }
}

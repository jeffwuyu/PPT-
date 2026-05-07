package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}

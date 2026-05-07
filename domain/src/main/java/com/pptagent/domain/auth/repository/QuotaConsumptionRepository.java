package com.pptagent.domain.auth.repository;

import com.pptagent.domain.auth.QuotaConsumptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaConsumptionRepository extends JpaRepository<QuotaConsumptionEntity, Long> {
}

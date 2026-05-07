package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("quota_consumptions")
public class QuotaConsumptionEntity {

    @TableId
    private Long id;
    private Long userId;
    private String quotaType;
    private int amount;
    private String businessKey;
    private Instant createdAt;

    @TableField(exist = false)
    private UserEntity user;

    protected QuotaConsumptionEntity() {
    }

    public QuotaConsumptionEntity(UserEntity user, String quotaType, int amount, String businessKey) {
        this.user = user;
        this.userId = user.getId();
        this.quotaType = quotaType;
        this.amount = amount;
        this.businessKey = businessKey;
        this.createdAt = Instant.now();
    }
}

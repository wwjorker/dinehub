package com.dinehub.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillReconciliationVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long couponId;
    private Integer dbRemainingStock;
    private Integer dbHolderCount;
    private Integer expectedRedisStock;
    private Integer redisAvailableStock;
    private Long redisProtectedUserCount;
    private Integer activeReservationCount;
    private Integer settledCount;
    private Integer compensatingCount;
    private Integer compensatedCount;
    private Integer stockDrift;
    private Boolean consistent;
}

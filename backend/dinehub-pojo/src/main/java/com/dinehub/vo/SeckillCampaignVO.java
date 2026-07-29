package com.dinehub.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class SeckillCampaignVO implements Serializable {
    private Long id;
    private String title;
    private Integer discountAmount;
    private Integer minAmount;
    private Integer totalStock;
    private Integer remainingStock;
    private Long serverTimeEpochMs;
    private Long startTimeEpochMs;
    private Long endTimeEpochMs;
    private String phase;
}

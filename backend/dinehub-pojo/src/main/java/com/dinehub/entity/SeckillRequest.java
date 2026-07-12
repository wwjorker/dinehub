package com.dinehub.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MySQL terminal ledger for one Redis admission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillRequest implements Serializable {

    public static final Integer SETTLED = 1;
    public static final Integer COMPENSATING = 2;
    public static final Integer COMPENSATED = 3;

    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long couponId;
    private Long userId;
    private Integer status;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

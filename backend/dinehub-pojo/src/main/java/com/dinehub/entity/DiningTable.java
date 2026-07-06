package com.dinehub.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 桌台
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiningTable implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //桌台名称
    private String name;

    //扫码点单令牌,印在桌台二维码里
    private String qrToken;

    //状态 0停用 1启用
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}

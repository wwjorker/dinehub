package com.dinehub.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 桌台视图对象,附带扫码点单入口地址
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiningTableVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String qrToken;

    private Integer status;

    //顾客扫码后打开的点单地址(二维码内容)
    private String orderUrl;

    private LocalDateTime updateTime;
}

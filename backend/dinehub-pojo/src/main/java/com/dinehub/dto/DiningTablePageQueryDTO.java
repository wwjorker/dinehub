package com.dinehub.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DiningTablePageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    //桌台名称,模糊查询
    private String name;

    //状态 0停用 1启用
    private Integer status;
}

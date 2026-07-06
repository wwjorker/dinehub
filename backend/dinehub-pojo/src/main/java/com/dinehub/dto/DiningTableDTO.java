package com.dinehub.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DiningTableDTO implements Serializable {

    private Long id;

    //桌台名称
    private String name;
}

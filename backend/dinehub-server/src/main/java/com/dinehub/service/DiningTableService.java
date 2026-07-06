package com.dinehub.service;

import com.dinehub.dto.DiningTableDTO;
import com.dinehub.dto.DiningTablePageQueryDTO;
import com.dinehub.entity.DiningTable;
import com.dinehub.result.PageResult;

public interface DiningTableService {

    /**
     * 新增桌台,自动生成扫码点单令牌
     */
    DiningTable save(DiningTableDTO diningTableDTO);

    /**
     * 桌台分页查询
     */
    PageResult pageQuery(DiningTablePageQueryDTO diningTablePageQueryDTO);

    /**
     * 修改桌台名称
     */
    void update(DiningTableDTO diningTableDTO);

    /**
     * 启用/停用桌台
     */
    void startOrStop(Integer status, Long id);

    /**
     * 删除桌台
     */
    void deleteById(Long id);

    /**
     * 根据id查询桌台
     */
    DiningTable getById(Long id);

    /**
     * 根据扫码令牌查询可用桌台
     */
    DiningTable getAvailableByToken(String qrToken);
}

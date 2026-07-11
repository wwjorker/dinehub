package com.dinehub.service;

import com.dinehub.dto.CouponDTO;
import com.dinehub.dto.CouponPageQueryDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.result.PageResult;
import com.dinehub.vo.UserCouponVO;

import java.util.List;

public interface CouponService {

    /**
     * 新增优惠券(草稿态)
     */
    Coupon save(CouponDTO couponDTO);

    /**
     * 分页查询
     */
    PageResult pageQuery(CouponPageQueryDTO couponPageQueryDTO);

    /**
     * 发布秒杀券:上架 + 预热Redis库存
     */
    void publish(Long id);

    /**
     * 抢券入口,按配置走 Redis+Lua+MQ 或 DB 行锁扣减
     */
    void seckill(Long couponId, Long userId);

    /**
     * 我的券列表
     */
    List<UserCouponVO> listByUserId(Long userId);

    /**
     * 原子且幂等地回补一个抢购名额(同一requestId只补偿一次)
     */
    boolean compensate(Long couponId, Long userId, String requestId);
}

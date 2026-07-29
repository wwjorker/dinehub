package com.dinehub.service;

import com.dinehub.dto.CouponDTO;
import com.dinehub.dto.CouponPageQueryDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.result.PageResult;
import com.dinehub.vo.UserCouponVO;
import com.dinehub.vo.SeckillResultVO;
import com.dinehub.vo.SeckillTicketVO;
import com.dinehub.vo.SeckillCampaignVO;

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
    SeckillTicketVO seckill(Long couponId, Long userId);

    SeckillResultVO getSeckillResult(String requestId, Long userId);

    /**
     * 我的券列表
     */
    List<UserCouponVO> listByUserId(Long userId);

    /**
     * C端可见的进行中/未开始秒杀券
     */
    List<SeckillCampaignVO> listActiveSeckill();

    /**
     * 幂等重建秒杀Redis账本(预热失败/Redis重启后的恢复入口)
     */
    void rebuildSeckillCache(Long id);
}

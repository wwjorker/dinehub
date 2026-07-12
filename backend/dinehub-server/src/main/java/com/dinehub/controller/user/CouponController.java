package com.dinehub.controller.user;

import com.dinehub.context.BaseContext;
import com.dinehub.entity.Coupon;
import com.dinehub.ratelimit.RateLimit;
import com.dinehub.result.Result;
import com.dinehub.service.CouponService;
import com.dinehub.vo.UserCouponVO;
import com.dinehub.vo.SeckillResultVO;
import com.dinehub.vo.SeckillTicketVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-优惠券:限时秒杀抢券、我的券
 */
@RestController("userCouponController")
@RequestMapping("/user/coupon")
@Api(tags = "C端-优惠券接口")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping("/seckill/{couponId}")
    @ApiOperation("限时秒杀抢券")
    @RateLimit(key = "seckill", permitsPerSecond = 500, capacity = 1000)
    public Result<SeckillTicketVO> seckill(@PathVariable Long couponId) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(couponService.seckill(couponId, userId));
    }

    @GetMapping("/seckill/result/{requestId}")
    @ApiOperation("查询秒杀处理结果")
    public Result<SeckillResultVO> seckillResult(@PathVariable String requestId) {
        return Result.success(couponService.getSeckillResult(requestId, BaseContext.getCurrentId()));
    }

    @GetMapping("/list")
    @ApiOperation("我的优惠券")
    public Result<List<UserCouponVO>> list() {
        return Result.success(couponService.listByUserId(BaseContext.getCurrentId()));
    }

    @GetMapping("/available")
    @ApiOperation("进行中的秒杀活动")
    public Result<List<Coupon>> available() {
        return Result.success(couponService.listActiveSeckill());
    }
}

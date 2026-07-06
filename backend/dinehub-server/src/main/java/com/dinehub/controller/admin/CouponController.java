package com.dinehub.controller.admin;

import com.dinehub.dto.CouponDTO;
import com.dinehub.dto.CouponPageQueryDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.result.PageResult;
import com.dinehub.result.Result;
import com.dinehub.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券管理:创建、发布(预热秒杀库存)
 */
@RestController
@RequestMapping("/admin/coupon")
@Api(tags = "优惠券相关接口")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping
    @ApiOperation("新增优惠券")
    public Result<Coupon> save(@RequestBody CouponDTO couponDTO) {
        log.info("新增优惠券:{}", couponDTO);
        return Result.success(couponService.save(couponDTO));
    }

    @GetMapping("/page")
    @ApiOperation("优惠券分页查询")
    public Result<PageResult> page(CouponPageQueryDTO couponPageQueryDTO) {
        return Result.success(couponService.pageQuery(couponPageQueryDTO));
    }

    @PostMapping("/{id}/publish")
    @ApiOperation("发布优惠券并预热秒杀库存")
    public Result publish(@PathVariable Long id) {
        log.info("发布优惠券:{}", id);
        couponService.publish(id);
        return Result.success();
    }
}

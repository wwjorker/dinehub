package com.dinehub.mapper;

import com.dinehub.entity.UserCoupon;
import com.dinehub.vo.UserCouponVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface UserCouponMapper {

    /**
     * 落券;uk_user_coupon 唯一键冲突时抛异常,消费端以此实现幂等
     */
    @Insert("insert into user_coupon(user_id, coupon_id, status, create_time)" +
            " values (#{userId}, #{couponId}, #{status}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(UserCoupon userCoupon);

    List<UserCouponVO> listByUserId(Long userId);
}

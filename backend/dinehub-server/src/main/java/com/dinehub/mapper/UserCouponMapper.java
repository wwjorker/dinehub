package com.dinehub.mapper;

import com.dinehub.entity.UserCoupon;
import com.dinehub.vo.UserCouponVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 已持券用户id,发布/重新预热时用DB数据重建Redis防重集合
     */
    @Select("select user_id from user_coupon where coupon_id = #{couponId}")
    List<Long> listUserIdsByCouponId(Long couponId);

    @Select("select count(1) from user_coupon where user_id = #{userId} and coupon_id = #{couponId}")
    int countByUserAndCoupon(@Param("userId") Long userId, @Param("couponId") Long couponId);

    @Select("select count(1) from user_coupon where coupon_id = #{couponId}")
    int countByCouponId(Long couponId);
}

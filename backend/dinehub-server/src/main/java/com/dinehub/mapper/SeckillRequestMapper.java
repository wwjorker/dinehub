package com.dinehub.mapper;

import com.dinehub.entity.SeckillRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillRequestMapper {

    @Insert("insert ignore into seckill_request(request_id, coupon_id, user_id, status, last_error, create_time, update_time)" +
            " values(#{requestId}, #{couponId}, #{userId}, #{status}, #{lastError}, #{createTime}, #{updateTime})")
    int insertIgnore(SeckillRequest request);

    @Select("select * from seckill_request where request_id = #{requestId}")
    SeckillRequest getByRequestId(String requestId);

    @Update("update seckill_request set status = 3, last_error = #{lastError}, update_time = now()" +
            " where request_id = #{requestId} and status = 2")
    int markCompensated(@Param("requestId") String requestId, @Param("lastError") String lastError);

    @Select("select count(1) from seckill_request where coupon_id = #{couponId} and status = #{status}")
    int countByCouponAndStatus(@Param("couponId") Long couponId, @Param("status") Integer status);
}

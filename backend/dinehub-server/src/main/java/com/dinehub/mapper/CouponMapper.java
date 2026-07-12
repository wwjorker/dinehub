package com.dinehub.mapper;

import com.dinehub.annotation.AutoFill;
import com.dinehub.dto.CouponPageQueryDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.enumeration.OperationType;
import com.github.pagehelper.Page;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper {

    @Insert("insert into coupon(title, type, discount_amount, min_amount, total_stock, remaining_stock," +
            " seckill_start_time, seckill_end_time, status, create_time, update_time, create_user, update_user)" +
            " values" +
            " (#{title}, #{type}, #{discountAmount}, #{minAmount}, #{totalStock}, #{remainingStock}," +
            " #{seckillStartTime}, #{seckillEndTime}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @AutoFill(value = OperationType.INSERT)
    void insert(Coupon coupon);

    @Select("select * from coupon where id = #{id}")
    Coupon getById(Long id);

    /**
     * C端可见的已发布秒杀券(未过期),按开抢时间排列
     */
    @Select("select * from coupon where status = 1 and type = 2" +
            " and seckill_end_time > now() order by seckill_start_time")
    List<Coupon> listActiveSeckill();

    @Select("select id from coupon where status = 1 and type = 2" +
            " and seckill_end_time > date_sub(now(), interval 1 day)")
    List<Long> listReliableScanCouponIds();

    Page<Coupon> pageQuery(CouponPageQueryDTO couponPageQueryDTO);

    @AutoFill(value = OperationType.UPDATE)
    void update(Coupon coupon);

    /**
     * 条件发布:仅未发布(0)可转已发布(1)。影响行数为0说明已发布过,
     * 调用方据此拒绝重复发布,避免重置Redis防重账本导致同一用户重复抢到。
     */
    @Update("update coupon set status = 1 where id = #{id} and status = 0")
    int publishIfDraft(Long id);

    /**
     * 行级条件扣减:库存不足时影响行数为0,这是DB侧防超卖的关键
     */
    @Update("update coupon set remaining_stock = remaining_stock - 1" +
            " where id = #{id} and remaining_stock > 0")
    int deductStock(Long id);
}

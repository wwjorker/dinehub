package com.dinehub.mapper;

import com.github.pagehelper.Page;
import com.dinehub.dto.GoodsSalesDTO;
import com.dinehub.dto.OrdersPageQueryDTO;
import com.dinehub.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    @Select("select * from orders where user_id = #{userId} and request_id = #{requestId}")
    Orders getByUserIdAndRequestId(@Param("userId") Long userId,
                                   @Param("requestId") String requestId);

    /**
     * 根据订单号和用户id查询订单
     * @param orderNumber
     * @param userId
     */
    @Select("select * from orders where number = #{orderNumber} and user_id= #{userId}")
    Orders getByNumberAndUserId(String orderNumber, Long userId);

    /**
     * 根据订单号查询订单(支付回调线程没有用户上下文,只能按单号定位)
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 状态机条件支付:仅待支付(1)可转待接单(2)。
     * 影响行数为0说明订单已被超时取消或重复回调,调用方据此决定拒绝入账,
     * 与超时取消的条件更新共同构成完整的状态机竞态防护。
     */
    @Update("update orders set status = 2, pay_status = 1, checkout_time = #{checkoutTime}" +
            " where id = #{id} and status = 1")
    int markPaidIfPending(@Param("id") Long id, @Param("checkoutTime") LocalDateTime checkoutTime);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 状态机条件取消:仅待支付(1)可转已取消(6)。
     * 影响行数为0说明订单已支付或已取消,调用方据此实现幂等,
     * 也避免了取消与支付回调间的竞态。
     */
    @Update("update orders set status = 6, cancel_reason = #{reason}, cancel_time = #{cancelTime}" +
            " where id = #{id} and status = 1")
    int cancelIfPending(@Param("id") Long id, @Param("reason") String reason,
                        @Param("cancelTime") LocalDateTime cancelTime);

    /**
     * 乐观状态迁移:携带读取时观察到的状态作为条件,并发迁移只有一方成功。
     * 影响行数为0说明状态已被其他操作(支付回调/取消/接单)抢先改变,
     * 调用方应放弃本次操作并返回状态错误。所有订单状态变更的唯一入口。
     */
    int transitionStatus(@Param("o") Orders target, @Param("fromStatus") Integer fromStatus);

    /**
     * 退款确认:仅退款中(3)可转已退款(2),幂等
     */
    @Update("update orders set pay_status = 2, refund_next_retry_at = null, refund_last_error = null" +
            " where id = #{id} and pay_status = 3")
    int markRefunded(Long id);

    /**
     * 迟到支付:订单已取消后钱才到账,标记为退款中转入退款流程
     */
    @Update("update orders set pay_status = 3, refund_next_retry_at = now()," +
            " refund_attempt_count = 0, refund_last_error = null" +
            " where id = #{id} and status = 6 and pay_status <> 2")
    int markRefundPendingIfCancelled(Long id);

    /**
     * 到达重试时间的退款订单。每次处理都会推进 next_retry_at，避免固定批次被长期处理中记录占满。
     */
    @Select("select * from orders where pay_status = 3" +
            " and (refund_next_retry_at is null or refund_next_retry_at <= #{now})" +
            " order by coalesce(refund_next_retry_at, '1970-01-01 00:00:00'), id limit #{limit}")
    List<Orders> listRefundPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("update orders set refund_next_retry_at = #{nextRetryAt}," +
            " refund_attempt_count = refund_attempt_count + 1, refund_last_error = #{error}" +
            " where id = #{id} and pay_status = 3")
    int scheduleRefundRetry(@Param("id") Long id,
                            @Param("nextRetryAt") LocalDateTime nextRetryAt,
                            @Param("error") String error);

    /**
     * CLOSED/ABNORMAL 不再自动调用支付平台，移到人工介入区且不阻塞后续退款。
     */
    @Update("update orders set refund_next_retry_at = '9999-12-31 23:59:59'," +
            " refund_attempt_count = refund_attempt_count + 1, refund_last_error = #{error}" +
            " where id = #{id} and pay_status = 3")
    int markRefundIntervention(@Param("id") Long id, @Param("error") String error);

    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量
     * @param status
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     * 根据订单状态和下单时间查询订单
     * @param status
     * @param orderTime
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    /**
     * 根据动态条件统计营业额数据
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据动态条件统计订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 统计指定时间区间内的销量排名前10
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin,LocalDateTime end);
}

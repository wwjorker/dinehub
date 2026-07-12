package com.dinehub.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.dinehub.constant.MessageConstant;
import com.dinehub.constant.StatusConstant;
import com.dinehub.context.BaseContext;
import com.dinehub.dto.*;
import com.dinehub.entity.*;
import com.dinehub.exception.AddressBookBusinessException;
import com.dinehub.exception.OrderBusinessException;
import com.dinehub.exception.ShoppingCartBusinessException;
import com.dinehub.mapper.*;
import com.dinehub.result.PageResult;
import com.dinehub.service.OrderService;
import com.dinehub.utils.HttpClientUtil;
import com.dinehub.utils.WeChatPayUtil;
import com.dinehub.vo.OrderPaymentVO;
import com.dinehub.vo.OrderStatisticsVO;
import com.dinehub.vo.OrderSubmitVO;
import com.dinehub.vo.OrderVO;
import com.dinehub.websocket.WebSocketServer;
import com.dinehub.config.OrderCancelMQConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private DiningTableMapper diningTableMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 超时取消实现:mq = 下单投递延迟消息(默认);task = 仅依赖定时扫描(对照方案)
     */
    @Value("${dinehub.order.cancel-mode:mq}")
    private String cancelMode;

    //外卖固定配送费(元)
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("6");

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //1. 处理各种业务异常（地址簿为空、桌台无效、购物车数据为空）
        //堂食单带桌台令牌走扫码点单链路,外卖单走地址簿链路
        boolean dineIn = ordersSubmitDTO.getTableToken() != null && !ordersSubmitDTO.getTableToken().isEmpty();
        DiningTable diningTable = null;
        AddressBook addressBook = null;
        if (dineIn) {
            diningTable = diningTableMapper.getByToken(ordersSubmitDTO.getTableToken());
            if (diningTable == null || !StatusConstant.ENABLE.equals(diningTable.getStatus())) {
                throw new OrderBusinessException(MessageConstant.TABLE_NOT_AVAILABLE);
            }
        } else {
            addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
            //地址不存在或不属于当前用户都按无效地址处理,防止横向越权
            if (addressBook == null || !addressBook.getUserId().equals(BaseContext.getCurrentId())) {
                //抛出业务异常
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }
            //检查用户的收货地址是否超出配送范围
            //checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
        }

        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0){
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //2. 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        //订单号:时间戳+随机段防并发冲突,uk_orders_number唯一索引兜底
        orders.setNumber(System.currentTimeMillis()
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
        //金额以服务端购物车快照重算,不信任客户端提交值
        BigDecimal itemsAmount = shoppingCartList.stream()
                .map(c -> c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        //打包费由服务端按明细件数定价(外卖每件1元),不信任客户端提交值
        int totalItems = shoppingCartList.stream().mapToInt(ShoppingCart::getNumber).sum();
        BigDecimal packFee = dineIn ? BigDecimal.ZERO : BigDecimal.valueOf(totalItems);
        orders.setPackAmount(dineIn ? 0 : totalItems);
        //外卖收固定配送费,堂食没有配送环节
        BigDecimal deliveryFee = dineIn ? BigDecimal.ZERO : DELIVERY_FEE;
        orders.setAmount(itemsAmount.add(packFee).add(deliveryFee));
        //堂食H5不传配送/餐具选项,这两列非空,给出与外卖端一致的默认值
        if (orders.getDeliveryStatus() == null) {
            orders.setDeliveryStatus(1);
        }
        if (orders.getTablewareStatus() == null) {
            orders.setTablewareStatus(1);
        }
        if (dineIn) {
            //堂食单没有配送地址,履约落在桌台上
            orders.setFulfillmentType(Orders.DINE_IN);
            orders.setTableId(diningTable.getId());
            orders.setAddress(diningTable.getName());
            orders.setConsignee(diningTable.getName());
        } else {
            orders.setFulfillmentType(Orders.DELIVERY);
            orders.setAddress(addressBook.getDetail());
            orders.setPhone(addressBook.getPhone());
            orders.setConsignee(addressBook.getConsignee());
        }
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        //3. 向订单明细表插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        //4. 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //投递延迟消息,TTL到期后若仍未支付则由消费者取消;定时扫描保留为低频兜底
        if ("mq".equals(cancelMode)) {
            rabbitTemplate.convertAndSend(OrderCancelMQConfiguration.ORDER_DELAY_EXCHANGE,
                    OrderCancelMQConfiguration.ORDER_DELAY_ROUTING_KEY, orders.getId());
        }

        //5. 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    @Value("${dinehub.shop.address}")
    private String shopAddress;

    @Value("${dinehub.baidu.ak}")
    private String ak;

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //校验订单归属与状态,金额以数据库为准,不信任客户端
        Orders order = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                order.getAmount(), //支付金额，单位 元
                "食汇订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo, Integer payerTotalFen) {
        //回调线程没有用户上下文,按订单号定位
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            log.error("pay callback for unknown order {}", outTradeNo);
            return;
        }

        //实付金额必须与订单金额一致,否则拒绝入账并告警(金额单位:分)
        int expectedFen = ordersDB.getAmount().movePointRight(2).intValueExact();
        if (payerTotalFen == null || payerTotalFen != expectedFen) {
            log.error("pay callback amount mismatch for order {}: paid {} expected {}, rejected",
                    outTradeNo, payerTotalFen, expectedFen);
            return;
        }

        //状态机条件更新:订单已被超时取消时影响0行,迟到的回调不允许把已取消订单改回待接单
        int rows = orderMapper.markPaidIfPending(ordersDB.getId(), LocalDateTime.now());
        if (rows == 0) {
            log.error("pay callback for order {} in status {}, needs manual refund handling",
                    outTradeNo, ordersDB.getStatus());
            return;
        }

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type",1); // 1表示来单提醒 2表示客户催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号：" + outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    /**
     * 查询订单并校验归属:订单不存在或不属于当前用户一律按不存在处理,
     * 防止用户端凭订单id横向越权读取/操作他人订单
     */
    private Orders getOwnedOrder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null || !orders.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orders;
    }

    public OrderVO details(Long id) {
        // 根据id查询订单并校验归属,防止横向越权
        Orders orders = getOwnedOrder(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单并校验归属,防止横向越权
        Orders ordersDB = getOwnedOrder(id);

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //调用微信支付退款接口;退款单号独立于订单号,金额取库内实际值
            weChatPayUtil.refund(
                    ordersDB.getNumber(), //商户订单号
                    "R" + ordersDB.getNumber(), //商户退款单号(唯一退款请求)
                    ordersDB.getAmount(),//退款金额，单位 元
                    ordersDB.getAmount());//原订单金额

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 乐观状态迁移:以读取时的状态为条件,支付回调等并发操作抢先则本次取消失败
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        int rows = orderMapper.transitionStatus(orders, ordersDB.getStatus());
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 再来一单
     *
     * @param id
     */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 校验订单归属,防止横向越权
        getOwnedOrder(id);

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     *
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        //仅待接单(2)可转已接单,并发取消/超时抢先则接单失败
        int rows = orderMapper.transitionStatus(orders, Orders.TO_BE_CONFIRMED);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款;退款单号独立,金额取库内实际值
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    "R" + ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount());
            log.info("refund requested for order {}", ordersDB.getNumber());
        }

        // 乐观状态迁移:仅待接单可拒单
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        int rows = orderMapper.transitionStatus(orders, Orders.TO_BE_CONFIRMED);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        if (ordersDB == null || ordersDB.getStatus() >= Orders.COMPLETED) {
            //已完成/已取消的订单不允许管理端取消
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款;退款单号独立,金额取库内实际值
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    "R" + ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount());
            log.info("refund requested for order {}", ordersDB.getNumber());
        }

        // 乐观状态迁移:以读取时的状态为条件,并发变更抢先则取消失败
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        int rows = orderMapper.transitionStatus(orders, ordersDB.getStatus());
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 乐观状态迁移:仅已接单(3)可转派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        int rows = orderMapper.transitionStatus(orders, Orders.CONFIRMED);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 乐观状态迁移:仅派送中(4)可转完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        int rows = orderMapper.transitionStatus(orders, Orders.DELIVERY_IN_PROGRESS);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 客户催单
     * @param id
     */
    public void reminder(Long id) {
        // 根据id查询订单并校验归属,防止横向越权
        Orders ordersDB = getOwnedOrder(id);

        Map map = new HashMap();
        map.put("type",2); //1表示来单提醒 2表示客户催单
        map.put("orderId",id);
        map.put("content","订单号：" + ordersDB.getNumber());

        //通过websocket向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}

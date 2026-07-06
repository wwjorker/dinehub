package com.dinehub.service.impl;

import com.dinehub.dto.CouponDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.exception.SeckillBusinessException;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.mq.SeckillMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        //real template over a mocked manager so the callback actually runs
        ReflectionTestUtils.setField(couponService, "transactionTemplate",
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        ReflectionTestUtils.setField(couponService, "seckillMode", "redis");
    }

    private Coupon publishedCoupon() {
        return Coupon.builder()
                .id(10L)
                .type(Coupon.TYPE_SECKILL)
                .status(Coupon.STATUS_PUBLISHED)
                .seckillStartTime(LocalDateTime.now().minusMinutes(5))
                .seckillEndTime(LocalDateTime.now().plusMinutes(5))
                .totalStock(100)
                .remainingStock(100)
                .build();
    }

    @Test
    void seckillCouponRequiresTimeWindowOnCreate() {
        CouponDTO dto = new CouponDTO();
        dto.setType(Coupon.TYPE_SECKILL);
        dto.setTotalStock(100);

        assertThrows(SeckillBusinessException.class, () -> couponService.save(dto));
    }

    @Test
    void seckillRejectsUnpublishedCoupon() {
        Coupon draft = publishedCoupon();
        draft.setStatus(Coupon.STATUS_DRAFT);
        when(couponMapper.getById(10L)).thenReturn(draft);

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
    }

    @Test
    void seckillRejectsOutsideTimeWindow() {
        Coupon notStarted = publishedCoupon();
        notStarted.setSeckillStartTime(LocalDateTime.now().plusHours(1));
        when(couponMapper.getById(10L)).thenReturn(notStarted);
        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));

        Coupon ended = publishedCoupon();
        ended.setSeckillEndTime(LocalDateTime.now().minusHours(1));
        when(couponMapper.getById(10L)).thenReturn(ended);
        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
    }

    @Test
    void grantedSeckillSendsAsyncMessage() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(CouponServiceImpl.SECKILL_GRANTED);

        couponService.seckill(10L, 1L);

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(SeckillMessage.class));
    }

    @Test
    void soldOutSeckillDoesNotSendMessage() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(CouponServiceImpl.SECKILL_SOLD_OUT);

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SeckillMessage.class));
    }

    @Test
    void duplicateUserSeckillIsRejected() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(CouponServiceImpl.SECKILL_DUPLICATE);

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SeckillMessage.class));
    }

    @Test
    void dbModeDeductsStockWithRowCondition() {
        ReflectionTestUtils.setField(couponService, "seckillMode", "db");
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(couponMapper.deductStock(10L)).thenReturn(1);

        couponService.seckill(10L, 1L);

        verify(userCouponMapper).insert(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(SeckillMessage.class));
    }

    @Test
    void dbModeSoldOutWhenNoRowUpdated() {
        ReflectionTestUtils.setField(couponService, "seckillMode", "db");
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(couponMapper.deductStock(10L)).thenReturn(0);

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
        verify(userCouponMapper, never()).insert(any());
    }

    @Test
    void dbModeUniqueKeyBlocksSecondGrab() {
        ReflectionTestUtils.setField(couponService, "seckillMode", "db");
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(couponMapper.deductStock(10L)).thenReturn(1);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_user_coupon"))
                .when(userCouponMapper).insert(any());

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
    }

    @Test
    void publishPreheatsRedisStock() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());

        couponService.publish(10L);

        verify(valueOps).set(eq(CouponServiceImpl.STOCK_KEY_PREFIX + 10L), eq("100"));
        verify(couponMapper).update(any(Coupon.class));
    }

    @Test
    void listByUserIdDelegatesToMapper() {
        when(userCouponMapper.listByUserId(1L)).thenReturn(Collections.emptyList());
        assertEquals(0, couponService.listByUserId(1L).size());
    }
}

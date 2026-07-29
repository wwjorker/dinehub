package com.dinehub.service.impl;

import com.dinehub.dto.CouponDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.entity.SeckillRequest;
import com.dinehub.exception.SeckillBusinessException;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.mq.SeckillMessage;
import com.dinehub.seckill.SeckillMessagePublisher;
import com.dinehub.seckill.SeckillRedisLedger;
import com.dinehub.seckill.SeckillTerminalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private SeckillRedisLedger seckillRedisLedger;
    @Mock
    private SeckillMessagePublisher seckillMessagePublisher;
    @Mock
    private SeckillTerminalService seckillTerminalService;

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
    void zeroThresholdCouponCanBeCreated() {
        CouponDTO dto = new CouponDTO();
        dto.setTitle("无门槛券");
        dto.setType(Coupon.TYPE_SECKILL);
        dto.setDiscountAmount(1000);
        dto.setMinAmount(0);
        dto.setTotalStock(10);
        dto.setSeckillStartTime(LocalDateTime.now().plusMinutes(1));
        dto.setSeckillEndTime(LocalDateTime.now().plusMinutes(30));

        couponService.save(dto);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getMinAmount());
    }

    @Test
    void nonzeroThresholdCannotBeLowerThanDiscount() {
        CouponDTO dto = new CouponDTO();
        dto.setTitle("规则错误");
        dto.setType(Coupon.TYPE_SECKILL);
        dto.setDiscountAmount(1000);
        dto.setMinAmount(500);
        dto.setTotalStock(10);
        dto.setSeckillStartTime(LocalDateTime.now().plusMinutes(1));
        dto.setSeckillEndTime(LocalDateTime.now().plusMinutes(30));

        assertThrows(SeckillBusinessException.class, () -> couponService.save(dto));
        verify(couponMapper, never()).insert(any());
    }

    @Test
    void campaignListUsesEpochTimeForTimezoneIndependentClients() {
        when(couponMapper.listActiveSeckill()).thenReturn(Collections.singletonList(publishedCoupon()));

        assertEquals("LIVE", couponService.listActiveSeckill().get(0).getPhase());
        assertEquals(true, couponService.listActiveSeckill().get(0).getEndTimeEpochMs()
                > couponService.listActiveSeckill().get(0).getStartTimeEpochMs());
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
        when(seckillRedisLedger.admit(eq(10L), eq(1L), any(), any(Duration.class)))
                .thenReturn(CouponServiceImpl.SECKILL_GRANTED);

        couponService.seckill(10L, 1L);

        verify(seckillMessagePublisher).publish(any(SeckillMessage.class));
    }

    @Test
    void soldOutSeckillDoesNotSendMessage() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(seckillRedisLedger.admit(eq(10L), eq(1L), any(), any(Duration.class)))
                .thenReturn(CouponServiceImpl.SECKILL_SOLD_OUT);

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
        verify(seckillMessagePublisher, never()).publish(any(SeckillMessage.class));
    }

    @Test
    void duplicateUserSeckillIsRejected() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(seckillRedisLedger.admit(eq(10L), eq(1L), any(), any(Duration.class)))
                .thenReturn(CouponServiceImpl.SECKILL_DUPLICATE);

        assertThrows(SeckillBusinessException.class, () -> couponService.seckill(10L, 1L));
        verify(seckillMessagePublisher, never()).publish(any(SeckillMessage.class));
    }

    @Test
    void dbModeDeductsStockWithRowCondition() {
        ReflectionTestUtils.setField(couponService, "seckillMode", "db");
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(couponMapper.deductStock(10L)).thenReturn(1);

        couponService.seckill(10L, 1L);

        verify(userCouponMapper).insert(any());
        verify(seckillMessagePublisher, never()).publish(any(SeckillMessage.class));
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
        when(couponMapper.publishIfDraft(10L)).thenReturn(1);
        when(userCouponMapper.listUserIdsByCouponId(10L)).thenReturn(Collections.emptyList());

        couponService.publish(10L);

        verify(valueOps).set(eq(CouponServiceImpl.STOCK_KEY_PREFIX + 10L), eq("100"));
    }

    @Test
    void rebuildCacheRequiresPublishedCoupon() {
        Coupon draft = publishedCoupon();
        draft.setStatus(Coupon.STATUS_DRAFT);
        when(couponMapper.getById(10L)).thenReturn(draft);

        assertThrows(SeckillBusinessException.class, () -> couponService.rebuildSeckillCache(10L));
    }

    @Test
    void rebuildCacheReloadsLedgerFromDb() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(userCouponMapper.listUserIdsByCouponId(10L)).thenReturn(Collections.emptyList());
        when(seckillRedisLedger.acquireRebuildLock(eq(10L), any(Duration.class))).thenReturn("lock");
        when(seckillRedisLedger.awaitTerminalQuiescence(eq(10L), any(Duration.class))).thenReturn(true);
        when(seckillRedisLedger.allActiveRequestIds(10L)).thenReturn(Collections.emptySet());
        when(seckillRedisLedger.replaceLedgerIfLockOwned(eq(10L), eq("lock"), eq(100),
                eq(Collections.emptySet()), any(Duration.class), any(Duration.class))).thenReturn(true);

        couponService.rebuildSeckillCache(10L);

        verify(seckillRedisLedger).replaceLedgerIfLockOwned(eq(10L), eq("lock"), eq(100),
                eq(Collections.emptySet()), any(Duration.class), any(Duration.class));
        verify(couponMapper, never()).publishIfDraft(10L);
    }

    @Test
    void rebuildSubtractsActiveReservationAndProtectsItsUser() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(userCouponMapper.listUserIdsByCouponId(10L)).thenReturn(Collections.singletonList(1L));
        when(seckillRedisLedger.acquireRebuildLock(eq(10L), any(Duration.class))).thenReturn("lock");
        when(seckillRedisLedger.awaitTerminalQuiescence(eq(10L), any(Duration.class))).thenReturn(true);
        when(seckillRedisLedger.allActiveRequestIds(10L)).thenReturn(Collections.singleton("request-1"));
        when(seckillRedisLedger.get("request-1")).thenReturn(SeckillRedisLedger.PendingRequest.builder()
                .requestId("request-1").couponId(10L).userId(2L).status("PUBLISHED")
                .attempt(1).createdAt(1L).build());
        when(seckillRedisLedger.replaceLedgerIfLockOwned(eq(10L), eq("lock"), eq(99),
                eq(new java.util.LinkedHashSet<>(java.util.Arrays.asList(1L, 2L))),
                any(Duration.class), any(Duration.class))).thenReturn(true);

        couponService.rebuildSeckillCache(10L);

        verify(seckillRedisLedger).replaceLedgerIfLockOwned(eq(10L), eq("lock"), eq(99),
                eq(new java.util.LinkedHashSet<>(java.util.Arrays.asList(1L, 2L))),
                any(Duration.class), any(Duration.class));
        verify(seckillRedisLedger).releaseRebuildLock(10L, "lock");
    }

    @Test
    void republishIsRejectedAndDoesNotResetLedger() {
        //重复发布会清空防重集合,让已领用户重复通过Lua;条件更新必须挡住它
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(couponMapper.publishIfDraft(10L)).thenReturn(0);

        assertThrows(SeckillBusinessException.class, () -> couponService.publish(10L));
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void sendFailureLeavesRecoverablePendingRequest() {
        when(couponMapper.getById(10L)).thenReturn(publishedCoupon());
        when(seckillRedisLedger.admit(eq(10L), eq(1L), any(), any(Duration.class)))
                .thenReturn(CouponServiceImpl.SECKILL_GRANTED);
        when(seckillMessagePublisher.publish(any(SeckillMessage.class))).thenReturn(false);

        assertEquals("PENDING", couponService.seckill(10L, 1L).getStatus());
        verify(seckillMessagePublisher).publish(any(SeckillMessage.class));
    }

    @Test
    void listByUserIdDelegatesToMapper() {
        when(userCouponMapper.listByUserId(1L)).thenReturn(Collections.emptyList());
        assertEquals(0, couponService.listByUserId(1L).size());
    }

    @Test
    void settledTerminalIsReportedAsSuccess() {
        when(seckillTerminalService.get("request-1")).thenReturn(SeckillRequest.builder()
                .requestId("request-1").couponId(10L).userId(1L).status(SeckillRequest.SETTLED).build());

        assertEquals("SUCCESS", couponService.getSeckillResult("request-1", 1L).getStatus());
    }

    @Test
    void resultLookupRejectsDifferentUser() {
        when(seckillTerminalService.get("request-1")).thenReturn(SeckillRequest.builder()
                .requestId("request-1").couponId(10L).userId(2L).status(SeckillRequest.SETTLED).build());

        assertThrows(SeckillBusinessException.class,
                () -> couponService.getSeckillResult("request-1", 1L));
    }
}

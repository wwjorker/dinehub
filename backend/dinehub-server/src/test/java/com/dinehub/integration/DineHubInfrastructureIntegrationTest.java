package com.dinehub.integration;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.entity.SeckillRequest;
import com.dinehub.entity.Orders;
import com.dinehub.mapper.OrderMapper;
import com.dinehub.mq.SeckillMessage;
import com.dinehub.mq.SeckillOrderConsumer;
import com.dinehub.seckill.SeckillCompensationService;
import com.dinehub.seckill.SeckillMessagePublisher;
import com.dinehub.seckill.SeckillRedisLedger;
import com.dinehub.task.SeckillReliabilityTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "dinehub.seckill.scan-interval-millis=3600000",
                "dinehub.order.timeout-scan-cron=0 0 0 1 1 ?"
        })
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class DineHubInfrastructureIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("dinehub")
            .withUsername("dinehub")
            .withPassword("dinehub");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management-alpine"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("dinehub.datasource.host", MYSQL::getHost);
        registry.add("dinehub.datasource.port", () -> MYSQL.getMappedPort(3306));
        registry.add("dinehub.datasource.database", MYSQL::getDatabaseName);
        // Flyway's MySQL support reads session variables from performance_schema;
        // the container's database-scoped test user intentionally lacks that grant.
        registry.add("dinehub.datasource.username", () -> "root");
        registry.add("dinehub.datasource.password", MYSQL::getPassword);
        registry.add("dinehub.redis.host", REDIS::getHost);
        registry.add("dinehub.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("dinehub.redis.password", () -> "");
        registry.add("dinehub.redis.database", () -> 0);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private SeckillRedisLedger ledger;
    @Autowired
    private SeckillMessagePublisher publisher;
    @Autowired
    private SeckillOrderConsumer consumer;
    @Autowired
    private SeckillCompensationService compensationService;
    @Autowired
    private SeckillReliabilityTask reliabilityTask;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private AmqpAdmin rabbitAdmin;
    @Autowired
    @Qualifier("seckillBinding")
    private Binding seckillBinding;

    @Test
    void flywayBuildsTheCompleteSchemaFromEmptyMySql() {
        Integer migrations = jdbc.queryForObject("select count(*) from flyway_schema_history where success = 1",
                Integer.class);
        String latest = jdbc.queryForObject(
                "select version from flyway_schema_history where version is not null order by installed_rank desc limit 1",
                String.class);

        assertEquals(9, migrations);
        assertEquals("9", latest);
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = 'seckill_request'",
                Integer.class));
        String devAdminHash = jdbc.queryForObject("select password from employee where username='admin'", String.class);
        assertTrue(devAdminHash != null && devAdminHash.startsWith("$2"));
        assertFalse("$2a$10$guUgfBracBiCkF7VvcNm6OWu0PE./m22dBTxW2g.XfWO6zsS4VaIS".equals(devAdminHash));
    }

    @Test
    void freshCommonMigrationsDoNotLeaveTheKnownAdministrator() throws Exception {
        String database = "prod_probe";
        jdbc.execute("drop database if exists " + database);
        jdbc.execute("create database " + database + " character set utf8mb4 collate utf8mb4_unicode_ci");
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection connection = DriverManager.getConnection(url, "root", MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select count(*) from employee where username='admin'")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void pendingAdmissionIsRecoveredAndDeliveredThroughRabbit() throws Exception {
        long couponId = createCoupon(1);
        SeckillMessage message = admit(couponId, 91001L);

        reliabilityTask.recoverDueRequests();

        await(() -> holderCount(couponId) == 1, Duration.ofSeconds(15));
        assertEquals(0, dbStock(couponId));
        assertEquals(SeckillRequest.SETTLED, terminalStatus(message.getRequestId()));
    }

    @Test
    void duplicateConsumerDeliveryDeductsDbStockOnlyOnce() {
        long couponId = createCoupon(1);
        SeckillMessage message = SeckillMessage.builder()
                .requestId(UUID.randomUUID().toString()).couponId(couponId).userId(91002L)
                .admittedAt(System.currentTimeMillis()).build();

        consumer.onMessage(message);
        consumer.onMessage(message);

        assertEquals(1, holderCount(couponId));
        assertEquals(0, dbStock(couponId));
    }

    @Test
    void duplicateCompensationRefundsRedisOnlyOnce() {
        long couponId = createCoupon(1);
        SeckillMessage message = admit(couponId, 91003L);

        compensationService.compensate(message, "integration failure");
        compensationService.compensate(message, "duplicate dead letter");

        assertEquals(Integer.valueOf(1), ledger.availableStock(couponId));
        assertEquals(SeckillRequest.COMPENSATED, terminalStatus(message.getRequestId()));
        assertEquals(0, holderCount(couponId));
    }

    @Test
    void staleDbCompensationRecoversAfterRedisRequestLedgerLoss() {
        long couponId = createCoupon(1);
        SeckillMessage message = admit(couponId, 91006L);
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);
        jdbc.update("insert into seckill_request(request_id,coupon_id,user_id,status,last_error,create_time,update_time)" +
                        " values(?,?,?,2,'interrupted',?,?)",
                message.getRequestId(), couponId, message.getUserId(), old, old);
        redis.delete(SeckillRedisLedger.requestKey(message.getRequestId()));
        redis.opsForZSet().remove(SeckillRedisLedger.activeKey(couponId), message.getRequestId());

        reliabilityTask.recoverDueRequests();

        assertEquals(SeckillRequest.COMPENSATED, terminalStatus(message.getRequestId()));
        assertEquals(Integer.valueOf(1), ledger.availableStock(couponId));
        assertFalse(ledger.isUserProtected(couponId, message.getUserId()));
        assertEquals(0, holderCount(couponId));
        assertEquals(1, dbStock(couponId));
    }

    @Test
    void staleDbCompensationDoesNotRecreateExpiredStock() {
        long couponId = createCoupon(1);
        SeckillMessage message = admit(couponId, 91007L);
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);
        jdbc.update("insert into seckill_request(request_id,coupon_id,user_id,status,last_error,create_time,update_time)" +
                        " values(?,?,?,2,'interrupted',?,?)",
                message.getRequestId(), couponId, message.getUserId(), old, old);
        redis.delete(SeckillRedisLedger.requestKey(message.getRequestId()));
        redis.delete(SeckillRedisLedger.activeKey(couponId));
        redis.delete(SeckillRedisLedger.stockKey(couponId));
        redis.delete(SeckillRedisLedger.boughtKey(couponId));

        reliabilityTask.recoverDueRequests();

        assertEquals(SeckillRequest.COMPENSATED, terminalStatus(message.getRequestId()));
        assertNull(ledger.availableStock(couponId));
        assertEquals(1, dbStock(couponId));
    }

    @Test
    void expiredRebuildOwnerCannotOverwriteCurrentLedger() throws Exception {
        long couponId = createCoupon(4);
        String staleToken = ledger.acquireRebuildLock(couponId, Duration.ofMillis(100));
        assertNotNull(staleToken);
        Thread.sleep(200L);
        String currentToken = ledger.acquireRebuildLock(couponId, Duration.ofSeconds(30));
        assertNotNull(currentToken);
        try {
            assertFalse(ledger.replaceLedgerIfLockOwned(couponId, staleToken, 99,
                    Collections.singleton(99999L), Duration.ofHours(1), Duration.ofSeconds(30)));
            assertEquals(Integer.valueOf(4), ledger.availableStock(couponId));
            assertFalse(ledger.isUserProtected(couponId, 99999L));
        } finally {
            ledger.releaseRebuildLock(couponId, currentToken);
        }
    }

    @Test
    void settlementAndCompensationRaceHasOneTerminalWinner() throws Exception {
        long couponId = createCoupon(1);
        SeckillMessage message = admit(couponId, 91004L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> settlement = pool.submit(() -> runAfter(start, () -> consumer.onMessage(message)));
        Future<?> compensation = pool.submit(() -> runAfter(start,
                () -> compensationService.compensate(message, "race")));

        start.countDown();
        settlement.get();
        compensation.get();
        pool.shutdownNow();

        Integer status = terminalStatus(message.getRequestId());
        assertTrue(SeckillRequest.SETTLED.equals(status) || SeckillRequest.COMPENSATED.equals(status));
        if (SeckillRequest.SETTLED.equals(status)) {
            assertEquals(1, holderCount(couponId));
            assertEquals(0, dbStock(couponId));
            assertEquals(Integer.valueOf(0), ledger.availableStock(couponId));
        } else {
            assertEquals(0, holderCount(couponId));
            assertEquals(1, dbStock(couponId));
            assertEquals(Integer.valueOf(1), ledger.availableStock(couponId));
        }
    }

    @Test
    void mandatoryPublisherReturnKeepsUnroutableRequestRetryable() throws Exception {
        long couponId = createCoupon(1);
        SeckillMessage message = admit(couponId, 91005L);
        rabbitAdmin.removeBinding(seckillBinding);
        try {
            assertTrue(publisher.publish(message));
            await(() -> {
                SeckillRedisLedger.PendingRequest pending = ledger.get(message.getRequestId());
                return pending != null && "PENDING".equals(pending.getStatus())
                        && pending.getLastError() != null && !pending.getLastError().isEmpty();
            }, Duration.ofSeconds(10));
            assertEquals(0, holderCount(couponId));
        } finally {
            rabbitAdmin.declareBinding(seckillBinding);
        }
    }

    @Test
    void paymentAndCancellationConditionUpdatesCannotBothWin() throws Exception {
        String number = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("insert into orders(number, status, fulfillment_type, user_id, address_book_id, order_time," +
                        " pay_method, pay_status, amount, delivery_status, tableware_status)" +
                        " values(?, 1, 2, ?, null, now(), 1, 0, ?, 1, 1)",
                number, 92001L, new BigDecimal("10.00"));
        Long orderId = jdbc.queryForObject("select id from orders where number = ?", Long.class, number);
        assertNotNull(orderId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> paid = pool.submit(() -> {
            start.await();
            return orderMapper.markPaidIfPending(orderId, LocalDateTime.now());
        });
        Future<Integer> cancelled = pool.submit(() -> {
            start.await();
            return orderMapper.cancelIfPending(orderId, "timeout", LocalDateTime.now());
        });
        start.countDown();

        assertEquals(1, paid.get() + cancelled.get());
        pool.shutdownNow();
        Integer status = jdbc.queryForObject("select status from orders where id = ?", Integer.class, orderId);
        assertTrue(status == 2 || status == 6);
    }

    @Test
    void autoCompletionAndCancellationCannotBothWin() throws Exception {
        String number = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("insert into orders(number, status, fulfillment_type, user_id, address_book_id, order_time," +
                        " pay_method, pay_status, amount, delivery_status, tableware_status)" +
                        " values(?, 4, 2, ?, null, now(), 1, 1, ?, 1, 1)",
                number, 92002L, new BigDecimal("10.00"));
        Long orderId = jdbc.queryForObject("select id from orders where number = ?", Long.class, number);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> completed = pool.submit(() -> {
            start.await();
            return orderMapper.transitionStatus(Orders.builder().id(orderId).status(Orders.COMPLETED)
                    .deliveryTime(LocalDateTime.now()).build(), Orders.DELIVERY_IN_PROGRESS);
        });
        Future<Integer> cancelled = pool.submit(() -> {
            start.await();
            return orderMapper.transitionStatus(Orders.builder().id(orderId).status(Orders.CANCELLED)
                    .payStatus(Orders.REFUND_PENDING).cancelTime(LocalDateTime.now()).build(),
                    Orders.DELIVERY_IN_PROGRESS);
        });
        start.countDown();

        assertEquals(1, completed.get() + cancelled.get());
        pool.shutdownNow();
        Integer status = jdbc.queryForObject("select status from orders where id = ?", Integer.class, orderId);
        Integer payStatus = jdbc.queryForObject("select pay_status from orders where id = ?", Integer.class, orderId);
        assertTrue(status == Orders.COMPLETED || status == Orders.CANCELLED);
        if (status == Orders.COMPLETED) {
            assertEquals(Orders.PAID, payStatus);
        } else {
            assertEquals(Orders.REFUND_PENDING, payStatus);
        }
    }

    private long createCoupon(int stock) {
        String title = "integration-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("insert into coupon(title, type, discount_amount, min_amount, total_stock, remaining_stock," +
                        " seckill_start_time, seckill_end_time, status, create_time, update_time)" +
                        " values(?, 2, 100, 0, ?, ?, ?, ?, 1, ?, ?)",
                title, stock, stock, Timestamp.valueOf(now.minusMinutes(1)), Timestamp.valueOf(now.plusHours(1)),
                Timestamp.valueOf(now), Timestamp.valueOf(now));
        Long couponId = jdbc.queryForObject("select id from coupon where title = ?", Long.class, title);
        redis.opsForValue().set(SeckillRedisLedger.stockKey(couponId), String.valueOf(stock), Duration.ofHours(2));
        redis.delete(SeckillRedisLedger.boughtKey(couponId));
        return couponId;
    }

    private SeckillMessage admit(long couponId, long userId) {
        String requestId = UUID.randomUUID().toString();
        assertEquals(Long.valueOf(0L), ledger.admit(couponId, userId, requestId, Duration.ofHours(2)));
        return SeckillMessage.builder().requestId(requestId).couponId(couponId).userId(userId)
                .admittedAt(System.currentTimeMillis()).build();
    }

    private int holderCount(long couponId) {
        return jdbc.queryForObject("select count(*) from user_coupon where coupon_id = ?", Integer.class, couponId);
    }

    private int dbStock(long couponId) {
        return jdbc.queryForObject("select remaining_stock from coupon where id = ?", Integer.class, couponId);
    }

    private Integer terminalStatus(String requestId) {
        return jdbc.queryForObject("select status from seckill_request where request_id = ?",
                Integer.class, requestId);
    }

    private void await(BooleanSupplier condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100L);
        }
        assertTrue(condition.getAsBoolean(), "condition not met before timeout");
    }

    private void runAfter(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }
}

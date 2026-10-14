package com.dinehub.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class DemoImageMigrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("image_migration").withUsername("review").withPassword("review-local-only");

    private String database(String name) throws Exception {
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4");
        }
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + name;
    }

    private String scalar(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }

    @Test
    void freshDatabaseGetsAllMigrationsAndCategoryPlaceholders() throws Exception {
        String url = database("fresh_images");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        try (Connection c = DriverManager.getConnection(url, "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            assertEquals("16", scalar(s, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
            assertEquals("24", scalar(s, "SELECT COUNT(*) FROM dish"));
            assertEquals("22", scalar(s, "SELECT COUNT(*) FROM dish WHERE image LIKE '/h5/demo-food/placeholder-%'"));
            assertEquals("/h5/demo-food/placeholder-drinks.svg", scalar(s, "SELECT image FROM dish WHERE id=46"));
            assertEquals("/h5/demo-food/placeholder-steamed.svg", scalar(s, "SELECT image FROM dish WHERE id=58"));
            assertEquals("/h5/demo-food/charcoal-fish.png", scalar(s, "SELECT image FROM dish WHERE id=65"));
        }
    }

    @Test
    void upgradePreservesUploadsRenamedDishesAndUnrelatedRows() throws Exception {
        String url = database("upgrade_images");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("14").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("UPDATE dish SET image='/uploads/customer-photo.png' WHERE id=46");
            s.execute("UPDATE dish SET name='Custom renamed dish' WHERE id=47");
            s.execute("UPDATE dish SET category_id=19 WHERE id=48");
            s.execute("INSERT INTO dish (id,name,category_id,price,image,status) VALUES " +
                    "(99001,'Custom dish',11,10,'/h5/demo-food/garlic-greens.png',1)");
        }
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        try (Connection c = DriverManager.getConnection(url, "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            assertEquals("/uploads/customer-photo.png", scalar(s, "SELECT image FROM dish WHERE id=46"));
            assertEquals("/h5/demo-food/two-person-set.png", scalar(s, "SELECT image FROM dish WHERE id=47"));
            assertEquals("/h5/demo-food/charcoal-fish.png", scalar(s, "SELECT image FROM dish WHERE id=48"));
            assertEquals("/h5/demo-food/garlic-greens.png", scalar(s, "SELECT image FROM dish WHERE id=99001"));
            assertEquals("/h5/demo-food/placeholder-staples.svg", scalar(s, "SELECT image FROM dish WHERE id=49"));
            assertEquals("25", scalar(s, "SELECT COUNT(*) FROM dish"));
        }
    }

    @Test
    void commentMigrationPreservesColumnDefinitionsAndExistingOrderData() throws Exception {
        String url = database("order_comments");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("15").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO orders (number,status,fulfillment_type,user_id,order_time,pay_method,pay_status,original_amount,amount) " +
                    "VALUES ('comment-check',3,2,42,'2026-10-13 12:00:00',1,1,48.00,48.00)");
            String definitionsSql = "SELECT GROUP_CONCAT(CONCAT_WS(':',column_name,column_type,is_nullable," +
                    "COALESCE(column_default,'NULL'),extra) ORDER BY ordinal_position SEPARATOR '|') " +
                    "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders'";
            s.execute("SET SESSION group_concat_max_len=65535");
            String beforeDefinitions = scalar(s, definitionsSql);
            String beforeOrder = scalar(s, "SELECT CONCAT_WS(':',id,number,status,fulfillment_type,user_id," +
                    "order_time,pay_method,pay_status,original_amount,amount) FROM orders WHERE number='comment-check'");
            Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
            assertEquals(beforeDefinitions, scalar(s, definitionsSql));
            assertEquals(beforeOrder, scalar(s, "SELECT CONCAT_WS(':',id,number,status,fulfillment_type,user_id," +
                    "order_time,pay_method,pay_status,original_amount,amount) FROM orders WHERE number='comment-check'"));
            assertEquals("16", scalar(s, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history"));
            assertEquals("支付状态 0未支付 1已支付 2已退款 3退款中", scalar(s,
                    "SELECT column_comment FROM information_schema.columns WHERE table_schema=DATABASE() " +
                            "AND table_name='orders' AND column_name='pay_status'"));
        }
    }
}

-- Reset the local database to a clean demo state: removes benchmark seed
-- rows and all transactional data, keeping the curated menu, categories,
-- tables and the admin account. Run before recording or presenting:
--   docker exec -i dinehub-mysql mysql -uroot -proot dinehub < scripts/demo-reset.sql

-- transactional data
DELETE FROM user_coupon;
DELETE FROM order_detail;
DELETE FROM orders;
DELETE FROM shopping_cart;

-- benchmark coupons (any state)
DELETE FROM coupon WHERE title LIKE 'bench%';

-- benchmark menu and users
DELETE FROM dish_flavor WHERE dish_id IN (SELECT id FROM (SELECT id FROM dish WHERE name LIKE 'bench-dish-%') t);
DELETE FROM dish WHERE name LIKE 'bench-dish-%';
DELETE FROM user WHERE openid LIKE 'bench-user-%';

-- benchmark redis keys are namespaced under seckill:*; clear them too:
--   docker exec dinehub-redis redis-cli --scan --pattern "seckill:*" | xargs -r docker exec -i dinehub-redis redis-cli DEL

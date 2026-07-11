-- Business indexes the baseline schema lacked, plus a uniqueness guarantee
-- for order numbers now that they are generated with a random suffix.

ALTER TABLE `orders`
  ADD UNIQUE KEY `uk_orders_number` (`number`),
  ADD KEY `idx_orders_status_order_time` (`status`, `order_time`),
  ADD KEY `idx_orders_user` (`user_id`);

ALTER TABLE `order_detail`
  ADD KEY `idx_order_detail_order` (`order_id`);

ALTER TABLE `shopping_cart`
  ADD KEY `idx_shopping_cart_user` (`user_id`);

ALTER TABLE `dish`
  ADD KEY `idx_dish_category_status` (`category_id`, `status`);

ALTER TABLE `dish_flavor`
  ADD KEY `idx_dish_flavor_dish` (`dish_id`);

ALTER TABLE `setmeal`
  ADD KEY `idx_setmeal_category` (`category_id`);

ALTER TABLE `setmeal_dish`
  ADD KEY `idx_setmeal_dish_setmeal` (`setmeal_id`);

ALTER TABLE `address_book`
  ADD KEY `idx_address_book_user` (`user_id`);

ALTER TABLE `user_coupon`
  ADD KEY `idx_user_coupon_coupon` (`coupon_id`);

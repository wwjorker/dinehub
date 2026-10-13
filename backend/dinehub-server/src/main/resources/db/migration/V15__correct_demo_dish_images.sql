-- Correct only unchanged built-in demo image assignments from V13/V14.
-- Custom image URLs and renamed/reclassified dishes are deliberately preserved.
-- All placeholders explicitly identify a category; they are not dish photographs.

UPDATE dish SET image = '/h5/demo-food/placeholder-drinks.svg'
WHERE id = 46 AND name = '鲜柠气泡水' AND category_id = 11
  AND image = '/h5/demo-food/garlic-greens.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-drinks.svg'
WHERE id = 47 AND name = '古法酸梅汤' AND category_id = 11
  AND image = '/h5/demo-food/two-person-set.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-drinks.svg'
WHERE id = 48 AND name = '冰镇杨梅饮' AND category_id = 11
  AND image = '/h5/demo-food/charcoal-fish.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-staples.svg'
WHERE id = 49 AND name = '五常米饭' AND category_id = 12
  AND image = '/h5/demo-food/spicy-bullfrog.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-staples.svg'
WHERE id = 50 AND name = '手工葱油拌面' AND category_id = 12
  AND image = '/h5/demo-food/garlic-greens.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-fish.svg'
WHERE id = 51 AND name = '金汤酸菜鱼片' AND category_id = 20
  AND image = '/h5/demo-food/two-person-set.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-fish.svg'
WHERE id = 52 AND name = '藤椒沸腾鱼' AND category_id = 20
  AND image = '/h5/demo-food/charcoal-fish.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-fish.svg'
WHERE id = 53 AND name = '番茄浓汤鱼片' AND category_id = 20
  AND image = '/h5/demo-food/spicy-bullfrog.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-vegetables.svg'
WHERE id = 54 AND name = '蒜香油麦菜' AND category_id = 19
  AND image = '/h5/demo-food/garlic-greens.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-vegetables.svg'
WHERE id = 55 AND name = '白灼芥蓝' AND category_id = 19
  AND image = '/h5/demo-food/garlic-greens.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-vegetables.svg'
WHERE id = 56 AND name = '荷塘小炒' AND category_id = 19
  AND image = '/h5/demo-food/charcoal-fish.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-vegetables.svg'
WHERE id = 57 AND name = '干煸四季豆' AND category_id = 19
  AND image = '/h5/demo-food/garlic-greens.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-steamed.svg'
WHERE id = 58 AND name = '豉汁蒸凤爪' AND category_id = 18
  AND image = '/h5/demo-food/two-person-set.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-steamed.svg'
WHERE id = 59 AND name = '粉蒸排骨' AND category_id = 18
  AND image = '/h5/demo-food/two-person-set.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-steamed.svg'
WHERE id = 60 AND name = '清蒸时令鱼' AND category_id = 18
  AND image = '/h5/demo-food/two-person-set.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-steamed.svg'
WHERE id = 61 AND name = '剁椒蒸芋头' AND category_id = 18
  AND image = '/h5/demo-food/spicy-bullfrog.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-bullfrog.svg'
WHERE id = 63 AND name = '泡椒牛蛙煲' AND category_id = 17
  AND image = '/h5/demo-food/spicy-bullfrog.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-bullfrog.svg'
WHERE id = 64 AND name = '青花椒牛蛙' AND category_id = 17
  AND image = '/h5/demo-food/spicy-bullfrog.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-fish.svg'
WHERE id = 66 AND name = '麻辣烤鱼(整条)' AND category_id = 16
  AND image = '/h5/demo-food/garlic-greens.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-fish.svg'
WHERE id = 67 AND name = '蒜香烤鱼(整条)' AND category_id = 16
  AND image = '/h5/demo-food/charcoal-fish.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-soup.svg'
WHERE id = 68 AND name = '菌菇鸡汤' AND category_id = 21
  AND image = '/h5/demo-food/charcoal-fish.png';

UPDATE dish SET image = '/h5/demo-food/placeholder-soup.svg'
WHERE id = 69 AND name = '冬瓜排骨汤' AND category_id = 21
  AND image = '/h5/demo-food/spicy-bullfrog.png';

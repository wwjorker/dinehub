-- Development seed data: categories, a starter menu, flavor options and the
-- initial admin account (password stored as MD5 of the well-known dev default).

INSERT INTO `category` VALUES (11,1,'清爽饮品',10,1,'2026-06-01 10:00:00','2026-06-01 10:00:00',1,1);
INSERT INTO `category` VALUES (12,1,'米面主食',9,1,'2026-06-01 10:01:00','2026-06-01 10:01:00',1,1);
INSERT INTO `category` VALUES (13,2,'双人套餐',12,1,'2026-06-01 10:02:00','2026-06-01 10:02:00',1,1);
INSERT INTO `category` VALUES (15,2,'单人工作餐',13,1,'2026-06-01 10:03:00','2026-06-01 10:03:00',1,1);
INSERT INTO `category` VALUES (16,1,'炭火烤鱼',4,1,'2026-06-01 10:04:00','2026-06-01 10:04:00',1,1);
INSERT INTO `category` VALUES (17,1,'川味牛蛙',5,1,'2026-06-01 10:05:00','2026-06-01 10:05:00',1,1);
INSERT INTO `category` VALUES (18,1,'功夫蒸菜',6,1,'2026-06-01 10:06:00','2026-06-01 10:06:00',1,1);
INSERT INTO `category` VALUES (19,1,'时令小炒',7,1,'2026-06-01 10:07:00','2026-06-01 10:07:00',1,1);
INSERT INTO `category` VALUES (20,1,'沸腾鱼锅',8,1,'2026-06-01 10:08:00','2026-06-01 10:08:00',1,1);
INSERT INTO `category` VALUES (21,1,'暖心汤羹',11,1,'2026-06-01 10:09:00','2026-06-01 10:09:00',1,1);

INSERT INTO `dish` VALUES (46,'鲜柠气泡水',11,9.00,'/images/dish/d01.png','现切青柠,气泡足',1,'2026-06-01 11:00:00','2026-06-01 11:00:00',1,1);
INSERT INTO `dish` VALUES (47,'古法酸梅汤',11,8.00,'/images/dish/d02.png','乌梅山楂慢熬',1,'2026-06-01 11:01:00','2026-06-01 11:01:00',1,1);
INSERT INTO `dish` VALUES (48,'冰镇杨梅饮',11,10.00,'/images/dish/d03.png','当季杨梅',1,'2026-06-01 11:02:00','2026-06-01 11:02:00',1,1);
INSERT INTO `dish` VALUES (49,'五常米饭',12,3.00,'/images/dish/d04.png','粒粒分明',1,'2026-06-01 11:03:00','2026-06-01 11:03:00',1,1);
INSERT INTO `dish` VALUES (50,'手工葱油拌面',12,12.00,'/images/dish/d05.png','小葱现熬葱油',1,'2026-06-01 11:04:00','2026-06-01 11:04:00',1,1);
INSERT INTO `dish` VALUES (51,'金汤酸菜鱼片',20,58.00,'/images/dish/d06.png','黑鱼片,金汤酸爽',1,'2026-06-01 11:05:00','2026-06-01 11:05:00',1,1);
INSERT INTO `dish` VALUES (52,'藤椒沸腾鱼',20,62.00,'/images/dish/d07.png','鲜藤椒,麻香突出',1,'2026-06-01 11:06:00','2026-06-01 11:06:00',1,1);
INSERT INTO `dish` VALUES (53,'番茄浓汤鱼片',20,56.00,'/images/dish/d08.png','番茄熬底,酸甜开胃',1,'2026-06-01 11:07:00','2026-06-01 11:07:00',1,1);
INSERT INTO `dish` VALUES (54,'蒜香油麦菜',19,16.00,'/images/dish/d09.png','大火快炒',1,'2026-06-01 11:08:00','2026-06-01 11:08:00',1,1);
INSERT INTO `dish` VALUES (55,'白灼芥蓝',19,18.00,'/images/dish/d10.png','豉油白灼',1,'2026-06-01 11:09:00','2026-06-01 11:09:00',1,1);
INSERT INTO `dish` VALUES (56,'荷塘小炒',19,20.00,'/images/dish/d11.png','莲藕荸荠木耳',1,'2026-06-01 11:10:00','2026-06-01 11:10:00',1,1);
INSERT INTO `dish` VALUES (57,'干煸四季豆',19,18.00,'/images/dish/d12.png','肉末干煸',1,'2026-06-01 11:11:00','2026-06-01 11:11:00',1,1);
INSERT INTO `dish` VALUES (58,'豉汁蒸凤爪',18,26.00,'/images/dish/d13.png','软糯脱骨',1,'2026-06-01 11:12:00','2026-06-01 11:12:00',1,1);
INSERT INTO `dish` VALUES (59,'粉蒸排骨',18,36.00,'/images/dish/d14.png','米粉裹足,咸香',1,'2026-06-01 11:13:00','2026-06-01 11:13:00',1,1);
INSERT INTO `dish` VALUES (60,'清蒸时令鱼',18,48.00,'/images/dish/d15.png','当日鲜活',1,'2026-06-01 11:14:00','2026-06-01 11:14:00',1,1);
INSERT INTO `dish` VALUES (61,'剁椒蒸芋头',18,22.00,'/images/dish/d16.png','湖南剁椒',1,'2026-06-01 11:15:00','2026-06-01 11:15:00',1,1);
INSERT INTO `dish` VALUES (62,'紫苏爆炒牛蛙',17,68.00,'/images/dish/d17.png','紫苏提香,现杀现炒',1,'2026-06-01 11:16:00','2026-06-01 11:16:00',1,1);
INSERT INTO `dish` VALUES (63,'泡椒牛蛙煲',17,66.00,'/images/dish/d18.png','泡椒酸辣',1,'2026-06-01 11:17:00','2026-06-01 11:17:00',1,1);
INSERT INTO `dish` VALUES (64,'青花椒牛蛙',17,68.00,'/images/dish/d19.png','青花椒麻香',1,'2026-06-01 11:18:00','2026-06-01 11:18:00',1,1);
INSERT INTO `dish` VALUES (65,'秘制炭烤鲈鱼',16,58.00,'/images/dish/d20.png','炭火慢烤,外焦里嫩',1,'2026-06-01 11:19:00','2026-06-01 11:19:00',1,1);
INSERT INTO `dish` VALUES (66,'麻辣烤鱼(整条)',16,78.00,'/images/dish/d21.png','麻辣汤底,配宽粉',1,'2026-06-01 11:20:00','2026-06-01 11:20:00',1,1);
INSERT INTO `dish` VALUES (67,'蒜香烤鱼(整条)',16,72.00,'/images/dish/d22.png','蒜片铺满',1,'2026-06-01 11:21:00','2026-06-01 11:21:00',1,1);
INSERT INTO `dish` VALUES (68,'菌菇鸡汤',21,18.00,'/images/dish/d23.png','三种菌菇慢炖',1,'2026-06-01 11:22:00','2026-06-01 11:22:00',1,1);
INSERT INTO `dish` VALUES (69,'冬瓜排骨汤',21,16.00,'/images/dish/d24.png','清爽解腻',1,'2026-06-01 11:23:00','2026-06-01 11:23:00',1,1);

INSERT INTO `dish_flavor` VALUES (40,46,'温度','["常温","加冰","去冰"]');
INSERT INTO `dish_flavor` VALUES (41,47,'甜度','["标准糖","少糖","无糖"]');
INSERT INTO `dish_flavor` VALUES (42,48,'温度','["常温","加冰"]');
INSERT INTO `dish_flavor` VALUES (45,51,'辣度','["微辣","中辣","特辣"]');
INSERT INTO `dish_flavor` VALUES (46,52,'辣度','["微辣","中辣","特辣"]');
INSERT INTO `dish_flavor` VALUES (47,52,'忌口','["不要葱","不要蒜","不要香菜"]');
INSERT INTO `dish_flavor` VALUES (48,53,'忌口','["不要葱","不要香菜"]');
INSERT INTO `dish_flavor` VALUES (49,54,'忌口','["不要蒜"]');
INSERT INTO `dish_flavor` VALUES (50,57,'辣度','["不辣","微辣","中辣"]');
INSERT INTO `dish_flavor` VALUES (51,61,'辣度','["微辣","中辣"]');
INSERT INTO `dish_flavor` VALUES (52,62,'辣度','["微辣","中辣","特辣"]');
INSERT INTO `dish_flavor` VALUES (53,63,'辣度','["微辣","中辣","特辣"]');
INSERT INTO `dish_flavor` VALUES (54,64,'辣度','["中辣","特辣"]');
INSERT INTO `dish_flavor` VALUES (55,65,'忌口','["不要葱","不要蒜","不要香菜"]');
INSERT INTO `dish_flavor` VALUES (56,66,'辣度','["微辣","中辣","特辣"]');
INSERT INTO `dish_flavor` VALUES (57,66,'忌口','["不要葱","不要香菜"]');
INSERT INTO `dish_flavor` VALUES (58,67,'忌口','["不要葱","不要蒜"]');

INSERT INTO `employee` VALUES (1,'系统管理员','admin','e10adc3949ba59abbe56e057f20f883e','13900000001','1','320102199501015678',1,'2026-06-01 09:00:00','2026-06-01 09:00:00',1,1);

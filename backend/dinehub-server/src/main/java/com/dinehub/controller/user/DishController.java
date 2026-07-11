package com.dinehub.controller.user;

import com.dinehub.constant.StatusConstant;
import com.dinehub.entity.Dish;
import com.dinehub.result.Result;
import com.dinehub.service.DishService;
import com.dinehub.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 缓存开关,压测对照(直查DB vs Redis缓存)用,平时保持开启
     */
    @Value("${dinehub.cache.dish-enabled:true}")
    private boolean dishCacheEnabled;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {

        //构造redis中的key，规则：dish_分类id
        String key = "dish_" + categoryId;

        if (dishCacheEnabled) {
            //查询redis中是否存在菜品数据;空列表也是有效缓存,防止不存在的分类打穿到DB
            List<DishVO> cached = (List<DishVO>) redisTemplate.opsForValue().get(key);
            if (cached != null) {
                //如果存在，直接返回，无须查询数据库
                return Result.success(cached);
            }
        }

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        //如果不存在，查询数据库，将查询到的数据放入redis中
        List<DishVO> list = dishService.listWithFlavor(dish);
        if (dishCacheEnabled) {
            //TTL兜底最终一致(双删失败/应用重启也不会留下永久旧值);随机抖动防止集中过期
            long ttlMinutes = 60 + ThreadLocalRandom.current().nextInt(10);
            redisTemplate.opsForValue().set(key, list, ttlMinutes, TimeUnit.MINUTES);
        }

        return Result.success(list);
    }

}

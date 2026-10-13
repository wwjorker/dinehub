package com.dinehub.controller.user;

import com.dinehub.constant.JwtClaimsConstant;
import com.dinehub.constant.MessageConstant;
import com.dinehub.dto.UserLoginDTO;
import com.dinehub.entity.User;
import com.dinehub.properties.JwtProperties;
import com.dinehub.ratelimit.RateLimit;
import com.dinehub.result.Result;
import com.dinehub.service.UserService;
import com.dinehub.utils.JwtUtil;
import com.dinehub.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Api(tags = "C端用户相关接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 演示登录开关：按浏览器设备标识隔离演示用户，昵称仅作展示；生产环境必须关闭
     */
    @Value("${dinehub.demo-login.enabled:false}")
    private boolean demoLoginEnabled;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("微信登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
        log.info("收到微信用户登录请求");

        //微信登录
        User user = userService.wxLogin(userLoginDTO);

        //为微信用户生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build();
        return Result.success(userLoginVO);
    }

    /**
     * 演示登录：按设备标识隔离账号，供无微信环境的 H5 演示使用
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/demo-login")
    @ApiOperation("演示登录(昵称直登,仅演示环境)")
    @RateLimit(key = "demo-login", permitsPerSecond = 5, capacity = 10)
    public Result<UserLoginVO> demoLogin(@RequestBody UserLoginDTO userLoginDTO) {
        if (!demoLoginEnabled) {
            return Result.error(MessageConstant.LOGIN_FAILED);
        }
        User user = userService.demoLogin(userLoginDTO.getNickname(), userLoginDTO.getDeviceId());

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        return Result.success(UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build());
    }
}

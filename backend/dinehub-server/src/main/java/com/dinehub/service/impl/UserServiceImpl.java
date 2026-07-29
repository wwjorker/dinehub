package com.dinehub.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dinehub.constant.MessageConstant;
import com.dinehub.dto.UserLoginDTO;
import com.dinehub.entity.User;
import com.dinehub.exception.LoginFailedException;
import com.dinehub.mapper.UserMapper;
import com.dinehub.properties.WeChatProperties;
import org.springframework.dao.DuplicateKeyException;
import com.dinehub.service.UserService;
import com.dinehub.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    //微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());

        //判断openid是否为空，如果为空表示登录失败，抛出业务异常
        if(openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //判断当前用户是否为新用户
        User user = userMapper.getByOpenid(openid);

        //如果是新用户，自动完成注册
        if(user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }

        //返回这个用户对象
        return user;
    }

    /**
     * 演示登录按浏览器设备随机标识隔离账号，昵称只作为展示资料。
     * deviceId 不是密码，因此这个入口仍然只允许在开发环境开启。
     */
    public User demoLogin(String nickname, String deviceId) {
        //昵称仅允许中文/字母/数字,长度1~12,防注入与脏数据
        if (nickname == null || !nickname.trim().matches("[一-龥A-Za-z0-9]{1,12}")) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        if (deviceId == null || !deviceId.trim().matches("[A-Za-z0-9_-]{20,64}")) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        String displayName = nickname.trim();
        String openid = "demo:" + deviceId.trim();
        User user = userMapper.getByOpenid(openid);
        if (user == null) {
            try {
                user = User.builder()
                        .openid(openid)
                        .name(displayName)
                        .createTime(LocalDateTime.now())
                        .build();
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                //同一设备并发首登时，唯一键冲突后重查
                user = userMapper.getByOpenid(openid);
            }
        } else if (!displayName.equals(user.getName())) {
            userMapper.updateName(user.getId(), displayName);
            user.setName(displayName);
        }
        return user;
    }

    /**
     * 调用微信接口服务，获取微信用户的openid
     * @param code
     * @return
     */
    private String getOpenid(String code){
        //调用微信接口服务，获得当前微信用户的openid
        Map<String, String> map = new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}

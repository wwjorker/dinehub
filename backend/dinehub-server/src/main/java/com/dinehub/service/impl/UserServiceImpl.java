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
     * 演示登录:以 demo:{昵称} 作为openid查找或创建用户,
     * 让没有微信环境的H5演示端也能获得完整的用户身份
     */
    public User demoLogin(String nickname) {
        //昵称仅允许中文/字母/数字,长度1~12,防注入与脏数据
        if (nickname == null || !nickname.trim().matches("[一-龥A-Za-z0-9]{1,12}")) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        String openid = "demo:" + nickname.trim();
        User user = userMapper.getByOpenid(openid);
        if (user == null) {
            try {
                user = User.builder()
                        .openid(openid)
                        .name(nickname.trim())
                        .createTime(LocalDateTime.now())
                        .build();
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                //并发同昵称首登:唯一键冲突后重查,两个请求拿到同一用户
                user = userMapper.getByOpenid(openid);
            }
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

package com.dinehub.service;

import com.dinehub.dto.UserLoginDTO;
import com.dinehub.entity.User;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 演示登录:凭昵称找到或创建用户(无微信环境的H5演示用)
     * @param nickname
     * @return
     */
    User demoLogin(String nickname);
}

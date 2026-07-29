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
     * 演示登录:按当前设备隔离用户，昵称仅作为展示资料
     * @param nickname
     * @param deviceId
     * @return
     */
    User demoLogin(String nickname, String deviceId);
}

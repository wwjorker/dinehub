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
}

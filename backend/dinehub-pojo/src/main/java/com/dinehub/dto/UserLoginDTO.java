package com.dinehub.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
public class UserLoginDTO implements Serializable {

    private String code;

    //演示登录昵称(无微信环境的H5演示用)
    private String nickname;

}

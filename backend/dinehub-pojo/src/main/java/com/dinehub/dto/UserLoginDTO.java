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

    /**
     * 浏览器首次访问时生成并保存在本机的随机标识。
     * 仅用于开发环境隔离演示数据，不能替代生产环境的微信身份认证。
     */
    private String deviceId;

}

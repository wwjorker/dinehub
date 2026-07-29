package com.dinehub.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {

    @NotBlank(message = "请输入当前密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "新密码长度应为8到64位")
    private String newPassword;

    @NotBlank(message = "请再次输入新密码")
    private String confirmPassword;
}

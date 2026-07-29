package com.dinehub.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class PasswordResetDTO implements Serializable {

    @NotBlank(message = "请输入临时密码")
    @Size(min = 8, max = 64, message = "临时密码长度应为8到64位")
    private String newPassword;
}

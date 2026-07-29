package com.dinehub.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 32, message = "登录账号不能超过32个字符")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名不能超过32个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    private String sex;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^[0-9]{17}[0-9Xx]$", message = "身份证号格式不正确")
    private String idNumber;

    @Size(min = 8, max = 64, message = "临时密码长度应为8到64位")
    @ToString.Exclude
    private String password;

    private String role;

}

package com.dinehub.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@ApiModel(description = "员工登录时传递的数据模型")
public class EmployeeLoginDTO implements Serializable {

    @ApiModelProperty("用户名")
    @NotBlank(message = "请输入登录账号")
    @Size(max = 32, message = "登录账号不能超过32个字符")
    private String username;

    @ApiModelProperty("密码")
    @ToString.Exclude
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度应为6到64位")
    private String password;

}

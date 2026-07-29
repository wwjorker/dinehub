package com.dinehub.controller.admin;

import com.dinehub.constant.JwtClaimsConstant;
import com.dinehub.dto.EmployeeDTO;
import com.dinehub.dto.EmployeeLoginDTO;
import com.dinehub.dto.EmployeePageQueryDTO;
import com.dinehub.dto.PasswordEditDTO;
import com.dinehub.dto.PasswordResetDTO;
import com.dinehub.entity.Employee;
import com.dinehub.properties.JwtProperties;
import com.dinehub.result.PageResult;
import com.dinehub.result.Result;
import com.dinehub.service.EmployeeService;
import com.dinehub.service.AdminLoginAttemptService;
import com.dinehub.exception.BaseException;
import com.dinehub.exception.RateLimitException;
import com.dinehub.utils.JwtUtil;
import com.dinehub.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private AdminLoginAttemptService loginAttemptService;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation(value = "员工登录")
    public Result<EmployeeLoginVO> login(@Valid @RequestBody EmployeeLoginDTO employeeLoginDTO,
                                        HttpServletRequest request) {
        //登录 DTO 含明文密码，日志只记录账号标识
        log.info("员工登录，用户名：{}", employeeLoginDTO.getUsername());

        String remoteAddress = request.getRemoteAddr();
        if (loginAttemptService.isBlocked(employeeLoginDTO.getUsername(), remoteAddress)) {
            throw new RateLimitException("登录失败次数过多，请10分钟后重试");
        }
        Employee employee;
        try {
            employee = employeeService.login(employeeLoginDTO);
        } catch (BaseException ex) {
            loginAttemptService.recordFailure(employeeLoginDTO.getUsername(), remoteAddress);
            throw ex;
        }
        loginAttemptService.clear(employeeLoginDTO.getUsername(), remoteAddress);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .role(employee.getRole())
                .mustChangePassword(employee.getMustChangePassword())
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @ApiOperation("员工退出")
    public Result<String> logout() {
        return Result.success();
    }

    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增员工")
    public Result save(@Valid @RequestBody EmployeeDTO employeeDTO){
        log.info("新增员工，username={}", employeeDTO.getUsername());
        employeeService.save(employeeDTO);
        return Result.success();
    }

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("员工分页查询")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询，参数为：{}", employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用员工账号")
    public Result startOrStop(@PathVariable Integer status,Long id){
        log.info("启用禁用员工账号：{},{}",status,id);
        employeeService.startOrStop(status,id);
        return Result.success();
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询员工信息")
    public Result<Employee> getById(@PathVariable Long id){
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     * @return
     */
    @PutMapping
    @ApiOperation("编辑员工信息")
    public Result update(@Valid @RequestBody EmployeeDTO employeeDTO){
        log.info("编辑员工，id={}", employeeDTO.getId());
        employeeService.update(employeeDTO);
        return Result.success();
    }

    @PutMapping("/password")
    @ApiOperation("修改当前员工密码")
    public Result changePassword(@Valid @RequestBody PasswordEditDTO passwordEditDTO) {
        employeeService.changePassword(passwordEditDTO);
        return Result.success();
    }

    @PutMapping("/{id}/reset-password")
    @ApiOperation("管理员重置员工密码")
    public Result resetPassword(@PathVariable Long id,
                                @Valid @RequestBody PasswordResetDTO passwordResetDTO) {
        employeeService.resetPassword(id, passwordResetDTO);
        return Result.success();
    }
}

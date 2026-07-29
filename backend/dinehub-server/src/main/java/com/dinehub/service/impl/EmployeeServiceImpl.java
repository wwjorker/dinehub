package com.dinehub.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.dinehub.constant.MessageConstant;
import com.dinehub.constant.PasswordConstant;
import com.dinehub.constant.StatusConstant;
import com.dinehub.context.BaseContext;
import com.dinehub.dto.EmployeeDTO;
import com.dinehub.dto.EmployeeLoginDTO;
import com.dinehub.dto.EmployeePageQueryDTO;
import com.dinehub.dto.PasswordEditDTO;
import com.dinehub.dto.PasswordResetDTO;
import com.dinehub.entity.Employee;
import com.dinehub.exception.PasswordErrorException;
import com.dinehub.exception.PasswordEditFailedException;
import com.dinehub.exception.BaseException;
import com.dinehub.mapper.EmployeeMapper;
import com.dinehub.result.PageResult;
import com.dinehub.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    //BCrypt:每账户独立盐,替代原无盐MD5
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            throw new PasswordErrorException(MessageConstant.LOGIN_FAILED);
        }

        //密码比对:新账户是BCrypt;历史MD5账户校验通过后原子升级为BCrypt
        String stored = employee.getPassword();
        if (stored != null && stored.startsWith("$2")) {
            if (!passwordEncoder.matches(password, stored)) {
                //密码错误
                throw new PasswordErrorException(MessageConstant.LOGIN_FAILED);
            }
        } else {
            String md5 = DigestUtils.md5DigestAsHex(password.getBytes());
            if (!md5.equals(stored)) {
                //密码错误
                throw new PasswordErrorException(MessageConstant.LOGIN_FAILED);
            }
            //平滑迁移:条件更新仅在密码仍为旧哈希时生效,并发登录只会有一方升级
            employeeMapper.upgradePassword(employee.getId(), stored, passwordEncoder.encode(password));
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            throw new PasswordErrorException(MessageConstant.LOGIN_FAILED);
        }

        //3、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     *
     * @param employeeDTO
     */
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();

        //对象属性拷贝
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置账号的状态，默认正常状态 1表示正常 0表示锁定
        employee.setStatus(StatusConstant.ENABLE);

        //设置密码，默认密码123456(BCrypt存储)
        if (employeeDTO.getPassword() == null || employeeDTO.getPassword().trim().length() < 8) {
            throw new PasswordEditFailedException("请为新员工设置至少8位的临时密码");
        }
        employee.setPassword(passwordEncoder.encode(employeeDTO.getPassword()));
        employee.setRole(normalizeRole(employeeDTO.getRole()));
        employee.setMustChangePassword(true);

        //设置当前记录的创建时间和修改时间
        //employee.setCreateTime(LocalDateTime.now());
        //employee.setUpdateTime(LocalDateTime.now());

        //设置当前记录创建人id和修改人id
        //employee.setCreateUser(BaseContext.getCurrentId());
        //employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);
    }

    /**
     * 分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        // select * from employee limit 0,10
        //开始分页查询
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total, records);
    }

    /**
     * 启用禁用员工账号
     *
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        if (!Objects.equals(status, StatusConstant.ENABLE) && !Objects.equals(status, StatusConstant.DISABLE)) {
            throw new BaseException("账号状态不正确");
        }
        if (Objects.equals(BaseContext.getCurrentId(), id)) {
            throw new BaseException("不能禁用或启用当前登录账号");
        }
        // update employee set status = ? where id = ?

        /*Employee employee = new Employee();
        employee.setStatus(status);
        employee.setId(id);*/

        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee);
    }

    /**
     * 根据id查询员工
     *
     * @param id
     * @return
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new BaseException("员工不存在");
        }
        return employee;
    }

    /**
     * 编辑员工信息
     *
     * @param employeeDTO
     */
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setPassword(null);
        employee.setRole(normalizeRole(employeeDTO.getRole()));

        //employee.setUpdateTime(LocalDateTime.now());
        //employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.update(employee);
    }

    @Override
    public void changePassword(PasswordEditDTO dto) {
        Long currentId = BaseContext.getCurrentId();
        Employee employee = employeeMapper.getById(currentId);
        if (employee == null || !passwordMatches(dto.getOldPassword(), employee.getPassword())) {
            throw new PasswordEditFailedException("当前密码不正确");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new PasswordEditFailedException("两次输入的新密码不一致");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new PasswordEditFailedException("新密码不能与当前密码相同");
        }
        employeeMapper.updatePassword(currentId, passwordEncoder.encode(dto.getNewPassword()), false);
    }

    @Override
    public void resetPassword(Long id, PasswordResetDTO dto) {
        if (employeeMapper.getById(id) == null) {
            throw new BaseException("员工不存在");
        }
        employeeMapper.updatePassword(id, passwordEncoder.encode(dto.getNewPassword()), true);
    }

    private boolean passwordMatches(String raw, String stored) {
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(raw, stored);
        }
        return DigestUtils.md5DigestAsHex(raw.getBytes()).equals(stored);
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "OPERATOR" : role.trim().toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(normalized) && !"OPERATOR".equals(normalized)) {
            throw new BaseException("员工角色不正确");
        }
        return normalized;
    }
}

package com.dinehub.service;

import com.dinehub.dto.EmployeeDTO;
import com.dinehub.dto.EmployeeLoginDTO;
import com.dinehub.dto.EmployeePageQueryDTO;
import com.dinehub.entity.Employee;
import com.dinehub.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询员工
     * @param id
     * @return
     */
    Employee getById(Long id);

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    void update(EmployeeDTO employeeDTO);
}

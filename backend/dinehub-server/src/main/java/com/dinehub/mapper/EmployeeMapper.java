package com.dinehub.mapper;

import com.github.pagehelper.Page;
import com.dinehub.annotation.AutoFill;
import com.dinehub.dto.EmployeePageQueryDTO;
import com.dinehub.entity.Employee;
import com.dinehub.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {

    /**
     * 密码哈希原子升级:仅当存储值仍为旧哈希时替换,
     * 并发登录触发的多次升级只有一次生效,且不会覆盖用户主动改的新密码
     */
    @Update("update employee set password = #{newHash} where id = #{id} and password = #{oldHash}")
    int upgradePassword(@Param("id") Long id, @Param("oldHash") String oldHash, @Param("newHash") String newHash);

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 插入员工数据
     * @param employee
     */
    @Insert("insert into employee (name, username, password, phone, sex, id_number, create_time, update_time, create_user, update_user,status,role,must_change_password) " +
            "values " +
            "(#{name},#{username},#{password},#{phone},#{sex},#{idNumber},#{createTime},#{updateTime},#{createUser},#{updateUser},#{status},#{role},#{mustChangePassword})")
    @AutoFill(value = OperationType.INSERT)
    void insert(Employee employee);

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 根据主键动态修改属性
     * @param employee
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Employee employee);

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @Select("select * from employee where id = #{id}")
    Employee getById(Long id);

    @Update("update employee set password = #{password}, must_change_password = #{mustChange} where id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("mustChange") boolean mustChange);
}

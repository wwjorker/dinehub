package com.dinehub.mapper;

import com.dinehub.annotation.AutoFill;
import com.dinehub.dto.DiningTablePageQueryDTO;
import com.dinehub.entity.DiningTable;
import com.dinehub.enumeration.OperationType;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DiningTableMapper {

    @Insert("insert into dining_table(name, qr_token, status, create_time, update_time, create_user, update_user)" +
            " values" +
            " (#{name}, #{qrToken}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @AutoFill(value = OperationType.INSERT)
    void insert(DiningTable diningTable);

    Page<DiningTable> pageQuery(DiningTablePageQueryDTO diningTablePageQueryDTO);

    @Select("select * from dining_table where id = #{id}")
    DiningTable getById(Long id);

    @Select("select * from dining_table where qr_token = #{qrToken}")
    DiningTable getByToken(String qrToken);

    @AutoFill(value = OperationType.UPDATE)
    void update(DiningTable diningTable);

    @Delete("delete from dining_table where id = #{id}")
    void deleteById(Long id);
}

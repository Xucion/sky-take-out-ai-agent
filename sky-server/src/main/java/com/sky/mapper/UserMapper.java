package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 插入新对象
     * @param user
     */
    //要主键回显
    void insert(User user);

    @Select("select * from user where id = #{userId}")
    User getById(Long userId);

    @Select("select * from user where phone = #{phone}")
    User getByPhone(String phone);
}

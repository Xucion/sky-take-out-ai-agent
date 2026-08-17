package com.sky.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ShopMapper {

    @Select("select address from shop_info where id = 1")
    String getAddress();

    @Insert("insert into shop_info (id, address, update_time, update_user) " +
            "values (1, #{address}, #{updateTime}, #{updateUser}) " +
            "on duplicate key update address = values(address), " +
            "update_time = values(update_time), update_user = values(update_user)")
    void saveAddress(@Param("address") String address,
                     @Param("updateTime") LocalDateTime updateTime,
                     @Param("updateUser") Long updateUser);
}

package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/** 网页端用户注册参数 */
@Data
public class UserRegisterDTO implements Serializable {
    private String name;
    private String phone;
    private String password;
}

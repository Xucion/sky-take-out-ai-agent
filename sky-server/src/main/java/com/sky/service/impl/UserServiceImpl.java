package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User login(UserLoginDTO userLoginDTO) {
        if (userLoginDTO == null || !isValidPhone(userLoginDTO.getPhone())
                || !StringUtils.hasText(userLoginDTO.getPassword())) {
            throw new LoginFailedException("请输入正确的手机号和密码");
        }

        User user = userMapper.getByPhone(userLoginDTO.getPhone());
        if (user == null) {
            throw new LoginFailedException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new LoginFailedException(MessageConstant.PASSWORD_ERROR);
        }
        return user;
    }

    @Override
    public User register(UserRegisterDTO userRegisterDTO) {
        if (userRegisterDTO == null || !isValidPhone(userRegisterDTO.getPhone())) {
            throw new BaseException("请输入正确的11位手机号");
        }
        if (!StringUtils.hasText(userRegisterDTO.getPassword())
                || userRegisterDTO.getPassword().length() < 6
                || userRegisterDTO.getPassword().length() > 32) {
            throw new BaseException("密码长度应为6到32位");
        }
        if (userMapper.getByPhone(userRegisterDTO.getPhone()) != null) {
            throw new BaseException("该手机号已注册");
        }

        String name = StringUtils.hasText(userRegisterDTO.getName())
                ? userRegisterDTO.getName().trim()
                : "新用户" + userRegisterDTO.getPhone().substring(7);
        if (name.length() > 32) {
            throw new BaseException("昵称不能超过32个字符");
        }

        User user = User.builder()
                .name(name)
                .phone(userRegisterDTO.getPhone())
                .password(passwordEncoder.encode(userRegisterDTO.getPassword()))
                .createTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        return user;
    }

    private boolean isValidPhone(String phone) {
        return StringUtils.hasText(phone) && phone.matches("^1\\d{10}$");
    }
}

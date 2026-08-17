package com.sky.controller.admin;


import com.sky.dto.ShopAddressDTO;
import com.sky.context.BaseContext;
import com.sky.exception.BaseException;
import com.sky.mapper.ShopMapper;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "商铺相关接口")
@Slf4j
public class ShopController {
    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ShopMapper shopMapper;

    @Value("${sky.shop.address}")
    private String defaultShopAddress;

    /**
     * 设置店铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺营业状态")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置店铺营业状态: {}", status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(KEY, status);
        return Result.success();
    }

    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        log.info("获取到店铺营业状态为{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }

    @GetMapping("/address")
    @ApiOperation("获取店铺地址")
    public Result<String> getAddress() {
        String address = shopMapper.getAddress();
        if (!StringUtils.hasText(address)) {
            address = defaultShopAddress;
            shopMapper.saveAddress(address, LocalDateTime.now(), BaseContext.getCurrentId());
        }
        return Result.success(address);
    }

    @PutMapping("/address")
    @ApiOperation("设置店铺地址")
    public Result setAddress(@RequestBody ShopAddressDTO shopAddressDTO) {
        String address = shopAddressDTO == null ? null : shopAddressDTO.getAddress();
        if (!StringUtils.hasText(address) || address.trim().length() < 5 || address.trim().length() > 200) {
            throw new BaseException("请输入5到200个字符的完整店铺地址");
        }
        address = address.trim();
        shopMapper.saveAddress(address, LocalDateTime.now(), BaseContext.getCurrentId());
        log.info("设置店铺地址：{}", address);
        return Result.success();
    }
}

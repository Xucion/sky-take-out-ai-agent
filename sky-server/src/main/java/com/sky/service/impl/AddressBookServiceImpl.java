package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.BaseException;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    @Transactional
    public void save(AddressBook addressBook) {
        validateAddress(addressBook);
        Long userId = BaseContext.getCurrentId();
        addressBook.setUserId(userId);
        boolean makeDefault = Integer.valueOf(1).equals(addressBook.getIsDefault());
        if (makeDefault) {
            addressBookMapper.updateIsDefaultByUserId(AddressBook.builder().userId(userId).isDefault(0).build());
        }
        addressBook.setIsDefault(makeDefault ? 1 : 0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        assertOwnedByCurrentUser(addressBook);
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    @Transactional
    public void update(AddressBook addressBook) {
        validateAddress(addressBook);
        assertOwnedByCurrentUser(addressBookMapper.getById(addressBook.getId()));
        if (Integer.valueOf(1).equals(addressBook.getIsDefault())) {
            addressBookMapper.updateIsDefaultByUserId(AddressBook.builder()
                    .userId(BaseContext.getCurrentId()).isDefault(0).build());
        }
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        assertOwnedByCurrentUser(addressBookMapper.getById(addressBook.getId()));
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {
        assertOwnedByCurrentUser(addressBookMapper.getById(id));
        addressBookMapper.deleteById(id);
    }

    private void validateAddress(AddressBook addressBook) {
        if (addressBook == null) {
            throw new BaseException("地址信息不能为空");
        }
        if (!StringUtils.hasText(addressBook.getConsignee())) {
            throw new BaseException("收货人不能为空");
        }
        if (!StringUtils.hasText(addressBook.getPhone()) || !addressBook.getPhone().matches("^1\\d{10}$")) {
            throw new BaseException("请输入正确的11位手机号");
        }
        if (!isAreaCode(addressBook.getProvinceCode()) || !StringUtils.hasText(addressBook.getProvinceName())
                || !isAreaCode(addressBook.getCityCode()) || !StringUtils.hasText(addressBook.getCityName())
                || !isAreaCode(addressBook.getDistrictCode()) || !StringUtils.hasText(addressBook.getDistrictName())) {
            throw new BaseException("请选择完整有效的省、市、区县");
        }
        if (!StringUtils.hasText(addressBook.getDetail())) {
            throw new BaseException("详细地址不能为空");
        }
    }

    private boolean isAreaCode(String code) {
        return StringUtils.hasText(code) && code.matches("^\\d{6}$");
    }

    private void assertOwnedByCurrentUser(AddressBook addressBook) {
        if (addressBook == null || !Objects.equals(addressBook.getUserId(), BaseContext.getCurrentId())) {
            throw new BaseException("地址不存在或无权操作");
        }
    }

}

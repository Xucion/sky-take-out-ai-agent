package com.sky.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMapperAiToolContractTest {

    @Test
    void orderProgressQueryIsScopedByOrderAndUser() throws Exception {
        // 防止后续重构误删 user_id 条件，导致 Controller 层测试仍通过但真实 SQL 越权。
        Method method = OrderMapper.class.getMethod(
                "getProgressByIdAndUserId", Long.class, Long.class);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);

        String sql = String.join(" ", Arrays.asList(select.value()))
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(sql.contains("id = #{orderid}"));
        assertTrue(sql.contains("user_id = #{userid}"));
    }
}

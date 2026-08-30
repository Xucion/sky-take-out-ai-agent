package com.sky.ai.conversation.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 提供持久化层输入校验和 JDBC 可空值读取方法。
 */
final class PersistenceInputs {

    /**
     * 禁止实例化工具类。
     */
    private PersistenceInputs() {
    }

    /**
     * 校验必填字符串非空且不超过指定长度。
     */
    static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    /**
     * 校验可选字符串不超过指定长度。
     */
    static String optional(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    /**
     * 校验可选整数为非负数。
     */
    static Integer nonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    /**
     * 从结果集中读取允许为空的整数列。
     */
    static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.intValue();
    }
}

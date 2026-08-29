package com.sky.ai.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

final class PersistenceInputs {

    private PersistenceInputs() {
    }

    static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    static String optional(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    static Integer nonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.intValue();
    }
}

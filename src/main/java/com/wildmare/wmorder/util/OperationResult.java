package com.wildmare.wmorder.util;

public record OperationResult<T>(boolean success, String code, String detail, T value) {
    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<>(true, "ok", "", value);
    }
    public static <T> OperationResult<T> failure(String code, String detail) {
        return new OperationResult<>(false, code, detail == null ? "" : detail, null);
    }
}

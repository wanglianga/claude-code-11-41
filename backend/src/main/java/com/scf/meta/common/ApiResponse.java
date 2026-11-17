package com.scf.meta.common;

import java.util.Map;

/** 统一 API 响应包装。 */
public class ApiResponse<T> {
    public boolean ok;
    public String message;
    public T data;

    public ApiResponse() {}

    public ApiResponse(boolean ok, String message, T data) {
        this.ok = ok;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data);
    }

    public static ApiResponse<?> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public static ApiResponse<Map<String, Object>> okEmpty() {
        return new ApiResponse<>(true, null, Map.of());
    }
}

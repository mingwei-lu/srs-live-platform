package com.srs.live.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, UUID.randomUUID().toString().substring(0, 8));
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, "success", null, UUID.randomUUID().toString().substring(0, 8));
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, UUID.randomUUID().toString().substring(0, 8));
    }
}
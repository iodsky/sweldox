package com.iodsky.motorph.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseFactory {

    private ResponseFactory() {}

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        ApiResponse<T> res = new ApiResponse<>(
                true,
                message,
                data
        );
        return ResponseEntity.ok(res);
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data, PaginationMeta meta) {

        ApiResponse<T> res = new ApiResponse<>(
                true,
                message,
                data,
                meta
        );

        return ResponseEntity.ok(res);
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        ApiResponse<T> res = new ApiResponse<>(
                true,
                message,
                data
        );
        return new ResponseEntity<>(res, HttpStatus.CREATED);
    }

}

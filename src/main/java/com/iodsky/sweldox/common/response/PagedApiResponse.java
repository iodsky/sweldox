package com.iodsky.sweldox.common.response;

public class PagedApiResponse<T> extends ApiResponse<T> {
    private PaginationMeta meta;
        public PagedApiResponse(boolean success, String message, T data, PaginationMeta meta) {
        super(success, message, data);
        this.meta = meta;
    }
}

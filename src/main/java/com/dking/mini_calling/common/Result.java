package com.dking.mini_calling.common;
import lombok.Data;
/*
统一响应封装
 */


@Data
public class Result<T> {
    private int code;           // 与 HTTP 状态码保持一致，降低前端心智负担
    private String message;
    private T data;
    private long timestamp;

    private Result() {}

    public static <T> Result<T> success(T data) {
        return build(200, "success", data);
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
}


package com.team.cms.common.api;

/** All API responses have the same shape; HTTP status and code must agree. */
public final class Result<T> {
    private final int code;
    private final String message;
    private final T data;
    private Result(int code, String message, T data) { this.code = code; this.message = message; this.data = data; }
    public static <T> Result<T> ok(T data) { return new Result<>(200, "success", data); }
    public static <T> Result<T> error(int code, String message, T data) { return new Result<>(code, message, data); }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}

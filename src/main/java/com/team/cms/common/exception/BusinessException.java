package com.team.cms.common.exception;

import org.springframework.http.HttpStatus;

/** The message must be safe to display to the API consumer. */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    public BusinessException(HttpStatus status, String message) {
        super(message);
        if (!status.is4xxClientError() && !status.is5xxServerError()) {
            throw new IllegalArgumentException("业务异常必须使用错误状态码");
        }
        this.status = status;
    }
    public HttpStatus getStatus() { return status; }
}

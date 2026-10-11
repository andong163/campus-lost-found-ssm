package com.team.cms.common.exception;

import com.team.cms.common.api.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> business(BusinessException error) {
        return response(error.getStatus(), error.getMessage(), null);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> constraint(ConstraintViolationException error) {
        return response(HttpStatus.BAD_REQUEST, "请求参数不合法", null);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception error) {
        // Do not log exception messages/arguments here; DB failures may contain sensitive data.
        LOG.error("Unhandled exception type={}", error.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试", null);
    }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        return response(HttpStatus.BAD_REQUEST, "请求参数不合法", fieldErrors(error.getBindingResult()));
    }
    @Override protected ResponseEntity<Object> handleBindException(BindException error, HttpHeaders headers,
            HttpStatus status, WebRequest request) {
        return response(HttpStatus.BAD_REQUEST, "请求参数不合法", fieldErrors(error.getBindingResult()));
    }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        String message = status == HttpStatus.NOT_FOUND ? "接口不存在" :
            status == HttpStatus.METHOD_NOT_ALLOWED ? "请求方法不支持" :
            status == HttpStatus.UNSUPPORTED_MEDIA_TYPE ? "请求内容类型不支持" : "请求参数或格式不合法";
        return new ResponseEntity<>(Result.error(status.value(), message, null), headers, status);
    }
    private Map<String, String> fieldErrors(BindingResult binding) {
        Map<String, String> fields = new LinkedHashMap<>();
        binding.getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.isBindingFailure() ? "字段类型不正确" : error.getDefaultMessage()));
        return fields;
    }
    private ResponseEntity<Object> response(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(Result.error(status.value(), message, data));
    }
}

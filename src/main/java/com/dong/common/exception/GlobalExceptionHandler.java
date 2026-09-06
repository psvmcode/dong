package com.dong.common.exception;

import com.dong.common.constant.Constants;
import com.dong.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
/**
 * 全局异常处理器。
 */
@Slf4j
@RestControllerAdvice

public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.info("business exception code={} message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理参数绑定异常。
     *
     * @param ex 绑定异常
     * @return 失败响应
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(Constants.MESSAGE_PARAM_INVALID);
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, detail);
    }

    /**
     * 处理约束校验异常。
     *
     * @param ex 约束校验异常
     * @return 失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, ex.getMessage());
    }

    /**
     * 处理请求相关异常。
     *
     * @param ex 请求异常
     * @return 失败响应
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpRequestMethodNotSupportedException.class})
    public Result<Void> handleRequestException(Exception ex) {
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, ex.getMessage());
    }

    /**
     * 处理非法参数异常。
     *
     * @param ex 非法参数异常
     * @return 失败响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, ex.getMessage());
    }

    /**
     * 处理非法状态异常。
     *
     * @param ex 非法状态异常
     * @return 失败响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalState(IllegalStateException ex) {
        return Result.fail(Constants.CODE_OPERATION_CONFLICT, Constants.MESSAGE_OPERATION_CONFLICT, ex.getMessage());
    }

    /**
     * 处理主键冲突异常。
     *
     * @param ex 主键冲突异常
     * @return 失败响应
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.info("duplicate key rejected: {}", ex.getMessage());
        return Result.fail(Constants.CODE_OPERATION_CONFLICT, Constants.MESSAGE_OPERATION_CONFLICT, "record already exists");
    }

    /**
     * 处理未预期异常。
     *
     * @param ex 未预期异常
     * @return 失败响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return Result.fail(Constants.CODE_INTERNAL_ERROR, Constants.MESSAGE_INTERNAL_ERROR, ex.getMessage());
    }

}

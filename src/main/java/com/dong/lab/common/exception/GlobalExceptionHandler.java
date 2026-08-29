package com.dong.lab.common.exception;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.result.Result;
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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.info("business exception code={} message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(Constants.MESSAGE_PARAM_INVALID);
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, ex.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpRequestMethodNotSupportedException.class})
    public Result<Void> handleRequestException(Exception ex) {
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return Result.fail(Constants.CODE_PARAM_INVALID, Constants.MESSAGE_PARAM_INVALID, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalState(IllegalStateException ex) {
        return Result.fail(Constants.CODE_OPERATION_CONFLICT, Constants.MESSAGE_OPERATION_CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.info("duplicate key rejected: {}", ex.getMessage());
        return Result.fail(Constants.CODE_OPERATION_CONFLICT, Constants.MESSAGE_OPERATION_CONFLICT, "record already exists");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return Result.fail(Constants.CODE_INTERNAL_ERROR, Constants.MESSAGE_INTERNAL_ERROR, ex.getMessage());
    }

}

package com.project.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        FieldError first = e.getBindingResult().getFieldError();
        String msg = first == null ? "参数校验失败" : first.getField() + " " + first.getDefaultMessage();
        return Result.fail(400, msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public Result<Void> handleParam(Exception e) {
        return Result.fail(400, "请求参数格式不正确");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.fail(400, "请求数据格式不正确，时间请使用 yyyy-MM-dd HH:mm:ss");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "请求方法不支持");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(400, "图片大小超过限制，请选择不超过 5 MB 的图片");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingUpload(MissingServletRequestPartException e) {
        return Result.fail(400, "请选择需要上传的图片文件");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public Result<Void> handleConcurrency(ConcurrencyFailureException e) {
        log.warn("并发操作冲突", e);
        return Result.fail(409, "信息正在被处理，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统暂时无法处理请求，请稍后重试");
    }
}

package com.cpclaw.common.exception;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.common.security.SensitiveDataMasker;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final SensitiveDataMasker masker;

    public GlobalExceptionHandler(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(safeMessage(exception)));
    }

    @ExceptionHandler(IOException.class)
    public void handleIoException(IOException exception, HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, masker.mask(exception.getMessage()));
        }
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException exception, HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "流式任务执行超时，请重试");
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception) {
        return ResponseEntity.internalServerError().body(ApiResponse.error(safeMessage(exception)));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage() == null ? "请求处理失败" : masker.mask(exception.getMessage());
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...[truncated]";
    }
}

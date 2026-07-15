package com.cpclaw.common.exception;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.common.security.SensitiveDataMasker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

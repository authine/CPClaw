package com.cpclaw.common.exception;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.common.security.SensitiveDataMasker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SensitiveDataMasker masker;

    public GlobalExceptionHandler(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(masker.mask(exception.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception) {
        return ResponseEntity.internalServerError().body(ApiResponse.error(masker.mask(exception.getMessage())));
    }
}

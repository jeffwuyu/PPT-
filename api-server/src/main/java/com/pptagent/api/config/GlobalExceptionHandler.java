package com.pptagent.api.config;

import com.pptagent.common.api.ApiResponse;
import com.pptagent.common.error.PlatformException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlatformException.class)
    ResponseEntity<ApiResponse<Void>> handlePlatformException(PlatformException exception) {
        return ResponseEntity.status(statusFor(exception))
                .body(ApiResponse.failed(exception.getErrorCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiResponse.failed("BAD_REQUEST", message));
    }

    private HttpStatus statusFor(PlatformException exception) {
        return switch (exception.getErrorCode()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case EXTERNAL_SERVICE_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

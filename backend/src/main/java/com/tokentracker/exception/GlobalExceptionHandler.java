package com.tokentracker.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestAlertException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestAlertException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getEntityName(), ex.getErrorKey());
    }

    @ExceptionHandler(NotFoundAlertException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundAlertException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getEntityName(), ex.getErrorKey());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), "auth", "badcredentials");
    }

    @ExceptionHandler(WavesApiException.class)
    public ResponseEntity<Map<String, String>> handleWavesApi(WavesApiException ex) {
        LOG.warn("Waves API error: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage(), "waves", "upstreamerror");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, message, "request", "validation");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'",
                "request", "invalidparameter");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message,
            String entityName, String errorKey) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("entityName", entityName);
        body.put("errorKey", errorKey);
        return ResponseEntity.status(status).body(body);
    }
}

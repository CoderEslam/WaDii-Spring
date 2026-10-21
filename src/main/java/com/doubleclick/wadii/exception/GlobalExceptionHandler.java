package com.doubleclick.wadii.exception;

import com.doubleclick.wadii.utils.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Response<Object>> handleApi(ApiException e) {
        return build(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response<Object>> handleAccessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "You are not allowed to do this action");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Response<Object>> handleBadRequest(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request: " + e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response<Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Response<Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Object>> handleOther(Exception e) {
        // Spring's own web exceptions (415, 406, 404 for unknown paths, ...) keep their status.
        if (e instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.is5xxServerError() ? "Internal server error" : status.getReasonPhrase());
        }
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<Response<Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new Response<>(null, message, status.value()));
    }
}

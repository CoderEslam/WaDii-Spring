package com.doubleclick.wadii.common.api;

import com.doubleclick.wadii.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope for every JSON response.
 * <pre>
 * success: {"success":true,  "data":{...}, "message":"...", "statusCode":200, "timestamp":"..."}
 * error:   {"success":false, "code":"OFFER_EXPIRED", "message":"...", "statusCode":409, "timestamp":"...", "details":{...}}
 * </pre>
 * {@code statusCode} mirrors the HTTP status for older clients that read it from the body.
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        int statusCode,
        Map<String, String> details,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, null, message, data, 200, null, Instant.now());
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(success(data, "OK"));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(success(data, message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, null, message, data, 201, null, Instant.now()));
    }

    public static ResponseEntity<ApiResponse<Void>> error(ErrorCode code, String message, Map<String, String> details) {
        return ResponseEntity.status(code.status())
                .body(new ApiResponse<>(false, code.name(), message, null, code.status().value(), details, Instant.now()));
    }
}

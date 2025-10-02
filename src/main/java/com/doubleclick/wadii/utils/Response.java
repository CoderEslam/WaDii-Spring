package com.doubleclick.wadii.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Response<Data> {
    private Data data;
    private String message;
    private int statusCode;
    private LocalDateTime timestamp;

    public Response(Data data, String message, int statusCode) {
        this.data = data;
        this.message = message;
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ResponseEntity<Response<T>> response(T data, String message, ResponseType statusCode) {
        switch (statusCode) {
            case SUCCESS -> {
                return ResponseEntity.ok(new Response<>(data, message, statusCode.getCode(), LocalDateTime.now()));
            }
            case NOT_FOUND -> {
                return ResponseEntity.status(404).body(new Response<>(null, message, statusCode.getCode(), LocalDateTime.now()));
            }
            case ERROR -> {
                return ResponseEntity.status(400).body(new Response<>(null, message, statusCode.getCode(), LocalDateTime.now()));
            }
            case UNAUTHORIZED -> {
                return ResponseEntity.status(401).body(new Response<>(null, message, statusCode.getCode(), LocalDateTime.now()));
            }
            case FORBIDDEN -> {
                return ResponseEntity.status(403).body(new Response<>(null, message, statusCode.getCode(), LocalDateTime.now()));
            }
            default -> {
                return ResponseEntity.status(500).body(new Response<>(null, message, statusCode.getCode(), LocalDateTime.now()));
            }
        }
    }
}
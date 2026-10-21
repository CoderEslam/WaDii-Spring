package com.doubleclick.wadii.auth.controller;

import com.doubleclick.wadii.auth.dto.AuthRequest;
import com.doubleclick.wadii.auth.service.AuthService;
import com.doubleclick.wadii.utils.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Response<Object>> register(@RequestBody AuthRequest request) {
        return authService.registerUser(request);
    }

    @PostMapping("/login")
    public ResponseEntity<Response<Object>> login(@RequestBody AuthRequest request) {
        return authService.authenticateUser(request.getEmail(), request.getPassword(), request.getFcmToken());
    }
}

package com.doubleclick.wadii.auth.service;

import com.doubleclick.wadii.auth.dto.AuthRequest;
import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.utils.Response;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<Response<User>> registerUser(AuthRequest authRequest);

    ResponseEntity<Response<User>> authenticateUser(String username, String password,String fcm);

    ResponseEntity<Response<User>> showById(Long id);
}

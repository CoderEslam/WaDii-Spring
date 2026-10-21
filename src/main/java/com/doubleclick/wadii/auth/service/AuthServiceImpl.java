package com.doubleclick.wadii.auth.service;

import com.doubleclick.wadii.auth.config.JwtUtil;
import com.doubleclick.wadii.auth.dto.AuthRequest;
import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.entities.City;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.CityRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@AllArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ResponseEntity<Response<Object>> registerUser(AuthRequest authRequest) {
        // Public registration may only create USER (0) or PROVIDER (1) accounts.
        // Admins are created by the bootstrap runner or by an existing admin.
        if (authRequest.getUserType() == 2) {
            throw ApiException.forbidden("Admin accounts cannot be created through registration");
        }
        if (authRequest.getUserType() != 0 && authRequest.getUserType() != 1) {
            throw ApiException.badRequest("userType must be 0 (user) or 1 (provider)");
        }
        String email = authRequest.getEmail() == null ? "" : authRequest.getEmail().trim();
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("A valid email is required");
        }
        if (authRequest.getPassword() == null || authRequest.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (authRequest.getCityId() == null) {
            throw ApiException.badRequest("cityId is required");
        }
        boolean isProvider = authRequest.getUserType() == 1;
        if (isProvider && (authRequest.getProviderName() == null || authRequest.getProviderName().trim().isEmpty())) {
            throw ApiException.badRequest("providerName is required for providers");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict("User already exists with this email = " + email);
        }
        Optional<City> city = cityRepository.findById(authRequest.getCityId());
        if (city.isEmpty()) {
            throw ApiException.badRequest("this city id not exist with id = " + authRequest.getCityId());
        }

        User user = new User();
        user.setFirstName(authRequest.getFirstName());
        user.setLastName(authRequest.getLastName());
        user.setEmail(email);
        user.setPhone(authRequest.getPhone());
        user.setCity(city.get());
        user.setImage("download.jpeg");
        user.setBackgroundImage("download.jpeg");
        user.setFcmToken(authRequest.getFcmToken() == null ? "" : authRequest.getFcmToken());
        user.setRole(isProvider ? Role.PROVIDER : Role.USER);
        user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        user.setToken(token);
        user = userRepository.save(user);

        if (isProvider) {
            Provider provider = new Provider();
            provider.setUser(user);
            provider.setFollowersCount(0L);
            provider.setRate(0.0);
            provider.setName(authRequest.getProviderName().trim());
            providerRepository.save(provider);
            return success(user, token, "provider registered successfully");
        }
        return success(user, token, "User registered successfully");
    }

    @Override
    @Transactional
    public ResponseEntity<Response<Object>> authenticateUser(String email, String password, String fcm) {
        Optional<User> userOptional = email == null ? Optional.empty() : userRepository.findByEmail(email.trim());
        // Same answer for unknown email and wrong password so accounts cannot be enumerated.
        if (userOptional.isEmpty() || password == null
                || !passwordEncoder.matches(password, userOptional.get().getPassword())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        User user = userOptional.get();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        user.setToken(token);
        if (fcm != null && !fcm.isEmpty()) {
            user.setFcmToken(fcm);
        }
        if (user.getImage() == null) {
            user.setImage("download.jpeg");
        }
        if (user.getBackgroundImage() == null) {
            user.setBackgroundImage("download.jpeg");
        }
        user = userRepository.save(user);

        if (user.getRole() == Role.PROVIDER) {
            Optional<Provider> provider = providerRepository.findByUserId(user.getId());
            if (provider.isPresent()) {
                return success(fromProviderToUser(provider.get(), user), token, "success");
            }
        }
        return success(user, token, "success");
    }

    @Override
    public ResponseEntity<Response<User>> showById(Long id) {
        return userRepository.findById(id)
                .map(user -> Response.response(user, "success", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "User does not exist with this id = " + id, ResponseType.ERROR));
    }

    /**
     * The JWT is never serialized with a User entity (it would leak through nested users),
     * so the auth endpoints add it to the payload explicitly.
     */
    private ResponseEntity<Response<Object>> success(User user, String token, String message) {
        Map<String, Object> payload = objectMapper.convertValue(user, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        // Never replace the managed collections on the entity (orphanRemoval); default them in the payload instead.
        if (payload.get("orders") == null) payload.put("orders", Collections.emptyList());
        if (payload.get("following") == null) payload.put("following", Collections.emptyList());
        payload.put("token", token);
        return Response.response(payload, message, ResponseType.SUCCESS);
    }

    private User fromProviderToUser(Provider provider, User user) {
        return new User(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPassword(),
                user.getToken(),
                user.getFcmToken(),
                user.getImage(),
                user.getBackgroundImage(),
                user.getPhone(),
                user.getRole(),
                user.getRates(),
                user.getFollowing(),
                provider,
                user.getOrders(),
                user.getCity()
        );
    }
}

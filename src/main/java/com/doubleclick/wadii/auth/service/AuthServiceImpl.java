package com.doubleclick.wadii.auth.service;


import com.doubleclick.wadii.auth.config.JwtUtil;
import com.doubleclick.wadii.auth.dto.AuthRequest;
import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@AllArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public ResponseEntity<Response<User>> registerUser(AuthRequest authRequest) {
        if (userRepository.findByEmail(authRequest.getEmail()).isPresent()) {
            return Response.response(null, "User already exists with this email = " + authRequest.getEmail(), ResponseType.ERROR);
        }
        User user = new User();
        user.setFirstName(authRequest.getFirstName());
        user.setLastName(authRequest.getLastName());
        user.setEmail(authRequest.getEmail());
        user.setPhone(authRequest.getPhone());
        user.setFcmToken(authRequest.getFcmToken());
        if (authRequest.getUserType() == 0) {
            user.setRole(Role.USER);
        } else {
            user.setRole(Role.PROVIDER);
        }
        user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
        user = userRepository.save(user);
        user.setToken(jwtUtil.generateToken(user.getEmail(), user.getId()));
        user = userRepository.save(user);
        if (authRequest.getUserType() == 1) {
            Provider provider = new Provider();
            provider.setUser(user);
            provider.setFollowersCount(0L);
            provider.setRate(0.0);
            provider = providerRepository.save(provider);
            return Response.response(user, "provider registered successfully", ResponseType.SUCCESS);
        }
        return Response.response(user, "User registered successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<User>> authenticateUser(String email, String password) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            if (passwordEncoder.matches(password, user.get().getPassword())) {
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
                User userWithNewToken = user.get();
                if (userWithNewToken.getRole() == Role.PROVIDER) {
                    Optional<Provider> provider = providerRepository.findByUserId(userWithNewToken.getId());
                    if (provider.isPresent()) {
                        System.out.println(provider.get());
                        userWithNewToken.setToken(jwtUtil.generateToken(email, userWithNewToken.getId()));
                        userWithNewToken = userRepository.save(userWithNewToken);
                        return Response.response(fromProviderToUser(provider.get()), "success", ResponseType.SUCCESS);
                    }
                }
                userWithNewToken.setToken(jwtUtil.generateToken(email, userWithNewToken.getId()));
                userWithNewToken = userRepository.save(userWithNewToken);
                return Response.response(userWithNewToken, "success", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "Invalid password", ResponseType.ERROR);
            }
        } else {
            return Response.response(null, "User does not exist with this email = " + email, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<User>> showById(Long id) {
        return userRepository.findById(id)
                .map(user -> Response.response(user, "success", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "User does not exist with this id = " + id, ResponseType.ERROR));
    }

    private User fromProviderToUser(Provider provider) {
        return new User(
                provider.getUser().getId(),
                provider.getUser().getFirstName(),
                provider.getUser().getLastName(),
                provider.getUser().getEmail(),
                provider.getUser().getPassword(),
                provider.getUser().getToken(),
                provider.getUser().getFcmToken(),
                provider.getUser().getImage(),
                provider.getUser().getPhone(),
                provider.getUser().getRole(),
                provider.getUser().getRates(),
                provider.getUser().getFollowing(),
                provider,
                provider.getUser().getOrders()
        );
    }
}

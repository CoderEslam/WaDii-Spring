package com.doubleclick.wadii.common.security;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns a raw JWT into an {@link AuthUser}; shared by the HTTP filter and the WebSocket handshake. */
@Component
@RequiredArgsConstructor
public class TokenAuthenticator {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AuthUser authenticate(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (ExpiredJwtException e) {
            throw BusinessException.of(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw BusinessException.of(ErrorCode.TOKEN_INVALID);
        }
        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw BusinessException.of(ErrorCode.TOKEN_INVALID);
        }
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.of(ErrorCode.TOKEN_INVALID));
        Number tv = claims.get("tv", Number.class);
        if (tv == null || tv.intValue() != user.getTokenVersion()) {
            throw BusinessException.of(ErrorCode.TOKEN_INVALID, "This token was revoked, please log in again.");
        }
        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }
        return AuthUser.of(user);
    }
}

package com.doubleclick.wadii.identity.dto;

public record AuthResponse(String token, String tokenType, long expiresIn, UserView user, Long providerId) {
}

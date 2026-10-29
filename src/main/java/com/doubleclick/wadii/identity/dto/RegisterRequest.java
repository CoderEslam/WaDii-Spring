package com.doubleclick.wadii.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code userType}: 0 = USER, 1 = PROVIDER. 2 (ADMIN) is always refused.
 * {@code providerName} is required for providers.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 30) String phone,
        @NotNull Long cityId,
        @NotNull Integer userType,
        @Size(max = 150) String providerName,
        @Size(max = 512) String fcmToken) {
}

package com.doubleclick.wadii.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank String email, @NotBlank String password, @Size(max = 512) String fcmToken) {
}

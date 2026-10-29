package com.doubleclick.wadii.identity.dto;

import jakarta.validation.constraints.Size;

/** Partial update of the caller's own profile; null fields are left unchanged. */
public record UpdateProfileRequest(@Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                   @Size(max = 30) String phone, Long cityId, @Size(max = 512) String fcmToken) {
}

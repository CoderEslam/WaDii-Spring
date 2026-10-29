package com.doubleclick.wadii.identity.dto;

import com.doubleclick.wadii.identity.AdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateAdminRequest(@NotBlank @Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                 @NotBlank @Email String email, @NotBlank @Size(min = 12, max = 72) String password,
                                 @NotEmpty Set<AdminRole> adminRoles) {
}

package com.doubleclick.wadii.identity.dto;

import com.doubleclick.wadii.identity.AccountStatus;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.identity.Role;
import com.doubleclick.wadii.identity.User;

import java.time.Instant;
import java.util.Set;

/** The caller's own account (or an admin view). Never contains password hash, token or FCM token. */
public record UserView(Long id, String email, String firstName, String lastName, String phone, String image,
                       String backgroundImage, Long cityId, String cityName, Role role, int userType,
                       AccountStatus status, Set<AdminRole> adminRoles, Instant createdAt) {

    public static UserView of(User u) {
        return new UserView(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(), u.getImage(),
                u.getBackgroundImage(), u.getCity() == null ? null : u.getCity().getId(),
                u.getCity() == null ? null : u.getCity().getName(), u.getRole(), u.getRole().ordinal(), u.getStatus(),
                u.getRole() == Role.ADMIN ? Set.copyOf(u.getAdminRoles()) : Set.of(), u.getCreatedAt());
    }
}

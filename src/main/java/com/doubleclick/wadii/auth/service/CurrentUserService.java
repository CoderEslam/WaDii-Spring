package com.doubleclick.wadii.auth.service;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller from the JWT and centralizes the role/ownership checks,
 * so controllers never trust ids or roles sent by the client.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    /** For endpoints inherited from the base controller that have no Authentication parameter. */
    public User user() {
        return user(SecurityContextHolder.getContext().getAuthentication());
    }

    public User admin() {
        return admin(SecurityContextHolder.getContext().getAuthentication());
    }

    public User user(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw ApiException.unauthorized("Authentication is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> ApiException.unauthorized("Authenticated user not found"));
    }

    public User admin(Authentication authentication) {
        User user = user(authentication);
        if (user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("You are not admin to do this action");
        }
        return user;
    }

    /** The caller must have role PROVIDER; returns their provider profile. */
    public Provider provider(Authentication authentication) {
        return providerOf(user(authentication));
    }

    public Provider providerOf(User user) {
        if (user.getRole() != Role.PROVIDER) {
            throw ApiException.forbidden("You are not a provider to do this action");
        }
        return providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.forbidden("No provider profile found for this account"));
    }

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public void requireSelfOrAdmin(User caller, Long targetUserId) {
        if (!isAdmin(caller) && !caller.getId().equals(targetUserId)) {
            throw ApiException.forbidden("You can only access your own account");
        }
    }

    public void requireProviderOwnerOrAdmin(User caller, Provider provider) {
        if (isAdmin(caller)) return;
        if (provider.getUser() == null || !provider.getUser().getId().equals(caller.getId())) {
            throw ApiException.forbidden("You can only manage your own provider profile");
        }
    }
}

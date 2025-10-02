package com.doubleclick.wadii.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class AuthenticationUtil {

    public Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

}

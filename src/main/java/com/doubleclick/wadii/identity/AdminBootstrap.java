package com.doubleclick.wadii.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Creates the first super admin from {@code app.admin.email/password} when it does not exist. Admin accounts can
 * never be created through public registration; further admins are created by a super admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    /** Placeholder phone for the bootstrap account; staff phones are not used for business flows. */
    private static final String BOOTSTRAP_PHONE = "00000000000";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (users.existsByEmailIgnoreCase(email.trim())) {
            return;
        }
        if (password.length() < 12) {
            log.warn("Bootstrap admin not created: app.admin.password must have at least 12 characters");
            return;
        }
        User admin = new User();
        admin.setFullName("WaDii Super Admin");
        admin.setEmail(email.trim().toLowerCase(Locale.ROOT));
        admin.setPhone(BOOTSTRAP_PHONE);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setAdmin(true);
        admin.setAdminRoles(EnumSet.of(AdminRole.SUPER_ADMIN));
        users.save(admin);
        log.info("Bootstrap super admin created: {}", admin.getEmail());
    }
}

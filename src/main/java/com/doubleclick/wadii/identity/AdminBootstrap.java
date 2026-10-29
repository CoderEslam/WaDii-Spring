package com.doubleclick.wadii.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SUPER_ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD when no account with that email exists.
 * An existing non-admin account with that email is never promoted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        var existing = userRepository.findByEmailIgnoreCase(adminEmail.trim());
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                log.warn("ADMIN_EMAIL belongs to a non-admin account; it was not promoted.");
            }
            return;
        }
        User admin = new User();
        admin.setEmail(adminEmail.trim());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Admin");
        admin.setRole(Role.ADMIN);
        admin.getAdminRoles().add(AdminRole.SUPER_ADMIN);
        userRepository.save(admin);
        log.info("Bootstrap SUPER_ADMIN created.");
    }
}

package com.doubleclick.wadii.auth.service;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.entities.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD when that account does not exist yet.
 * Public registration can no longer create admins.
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
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        var existing = userRepository.findByEmail(adminEmail.trim());
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                log.warn("ADMIN_EMAIL {} belongs to a non-admin account; it was not promoted.", adminEmail);
            }
            return;
        }
        User admin = new User();
        admin.setFirstName("Admin");
        admin.setLastName("");
        admin.setEmail(adminEmail.trim());
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setImage("download.jpeg");
        admin.setBackgroundImage("download.jpeg");
        userRepository.save(admin);
        log.info("Bootstrap admin {} created.", adminEmail);
    }
}

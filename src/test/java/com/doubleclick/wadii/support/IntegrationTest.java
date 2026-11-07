package com.doubleclick.wadii.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base for integration tests: one real PostgreSQL (embedded, no Docker) per JVM, schema built by Flyway, a mutable
 * clock and the full Spring context with MockMvc. All classes share the context and database, so tests create
 * their own users and never assume they are alone in the data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestClockConfig.class, TestApi.class})
public abstract class IntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();
    private static final AtomicBoolean MATCHING_WIDENED = new AtomicBoolean();

    @Autowired
    protected TestApi api;
    @Autowired
    protected MutableClock clock;

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    /** Every provider created by any test serves Cairo, so the per-request cap is raised to keep matching deterministic. */
    @BeforeEach
    void widenMatching() {
        if (MATCHING_WIDENED.compareAndSet(false, true)) {
            api.config("MARKETPLACE", marketplace(200));
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected static Map<String, Object> marketplace(int maxTargets) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("responseDeadlineHours", 48);
        m.put("offerValidityDefaultHours", 24);
        m.put("offerValidityMinHours", 1);
        m.put("offerValidityMaxHours", 48);
        m.put("maxProviderTargets", maxTargets);
        m.put("defaultMatchRadiusKm", 30);
        m.put("noShowGraceMinutes", 30);
        m.put("rankingWindowDays", 90);
        m.put("providerCancellationFlagThreshold", 3);
        m.put("customerCancellationFlagThreshold", 5);
        m.put("disputeFlagThreshold", 3);
        return m;
    }
}

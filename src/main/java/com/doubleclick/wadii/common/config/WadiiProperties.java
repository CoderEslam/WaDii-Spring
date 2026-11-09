package com.doubleclick.wadii.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment settings ({@code wadii.*}). Business values that admins change at runtime (deadlines, fees, limits)
 * are not here: they live in versioned {@code config_versions} rows, see {@code settings.PlatformConfigService}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wadii")
public class WadiiProperties {

    private String timezone = "Africa/Cairo";
    private Storage storage = new Storage();
    private Fcm fcm = new Fcm();
    private RateLimit rateLimit = new RateLimit();
    private Jobs jobs = new Jobs();

    @Getter
    @Setter
    public static class Storage {
        private String root = "uploads";
    }

    @Getter
    @Setter
    public static class Fcm {
        private boolean enabled = false;
        private String projectId = "";
        private String credentialsPath = "";
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int loginPerMinute = 10;
        private int registerPerHour = 20;
    }

    @Getter
    @Setter
    public static class Jobs {
        /** Request deadlines, offer expiry and listing expiry. */
        private long expirationIntervalMs = 60_000;
        /** Subscription reminders and overdue checks. */
        private long subscriptionIntervalMs = 3_600_000;
    }
}

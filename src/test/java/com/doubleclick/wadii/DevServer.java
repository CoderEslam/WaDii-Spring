package com.doubleclick.wadii;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the API locally on an embedded PostgreSQL with demo data ({@code dev} profile), so the web client can be
 * developed without the hosted database. Started by {@code ./gradlew devServer}; data lives in build/dev-db.
 */
public final class DevServer {

    private DevServer() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of("build", "dev-db").toAbsolutePath();
        Files.createDirectories(dataDir);
        EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(54329).setDataDirectory(dataDir).setCleanDataDirectory(false).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // The process is exiting; nothing useful can be done with a close failure.
            }
        }));
        SpringApplication app = new SpringApplication(WaDiiApplication.class);
        app.setAdditionalProfiles("dev");
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        app.run(args);
    }
}

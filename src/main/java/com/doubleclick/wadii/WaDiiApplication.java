package com.doubleclick.wadii;

import com.doubleclick.wadii.common.config.WadiiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WadiiProperties.class)
public class WaDiiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaDiiApplication.class, args);
    }

    /** All business time comparisons go through this clock (UTC) so tests can move time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

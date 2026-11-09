package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter for login and registration, keyed by client IP. Good enough for a single
 * instance; a shared store would be needed when the API is scaled horizontally.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final WadiiProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public void checkLogin(String ip) {
        check("login:" + ip, properties.getRateLimit().getLoginPerMinute(), Duration.ofMinutes(1));
    }

    public void checkRegister(String ip) {
        check("register:" + ip, properties.getRateLimit().getRegisterPerHour(), Duration.ofHours(1));
    }

    private void check(String key, int limit, Duration window) {
        if (!properties.getRateLimit().isEnabled()) return;
        long now = clock.millis();
        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() <= now - window.toMillis()) {
                hits.pollFirst();
            }
            if (hits.size() >= limit) {
                throw BusinessException.of(ErrorCode.RATE_LIMITED);
            }
            hits.addLast(now);
        }
    }
}

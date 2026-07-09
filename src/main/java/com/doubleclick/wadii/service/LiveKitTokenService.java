package com.doubleclick.wadii.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LiveKitTokenService {

    private static final Logger logger = LoggerFactory.getLogger(LiveKitTokenService.class);
    // short-lived: only needs to survive the initial Room.connect(), not the whole call
    private static final long TOKEN_TTL_SECONDS = 10 * 60;

    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    @Value("${livekit.url}")
    private String url;

    private Key signingKey;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            logger.error("LiveKit api key/secret is not configured.");
            throw new IllegalArgumentException("LiveKit api key/secret is not configured.");
        }
        this.signingKey = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String getUrl() {
        return url;
    }

    public String buildAccessToken(String roomName, String identity, String participantName) {
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        videoGrant.put("roomJoin", true);
        videoGrant.put("room", roomName);
        videoGrant.put("canPublish", true);
        videoGrant.put("canSubscribe", true);
        videoGrant.put("canPublishData", true);

        Date now = new Date();
        Date expiry = new Date(now.getTime() + TOKEN_TTL_SECONDS * 1000);

        return Jwts.builder()
                .setIssuer(apiKey)
                .setSubject(identity)
                .claim("name", participantName)
                .claim("video", videoGrant)
                .setNotBefore(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}

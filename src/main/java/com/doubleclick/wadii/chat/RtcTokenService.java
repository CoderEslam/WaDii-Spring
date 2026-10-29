package com.doubleclick.wadii.chat;

import io.agora.media.RtcTokenBuilder2;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Short-lived RTC tokens (Agora or LiveKit). Tokens are never logged. */
@Component
public class RtcTokenService {

    private static final int AGORA_TTL_SECONDS = 3600;
    private static final long LIVEKIT_TTL_SECONDS = 600;

    public enum Vendor { AGORA, LIVEKIT }

    public record RtcToken(Vendor vendor, String token, String channelName, long uid, String appId, String url) {
    }

    @Value("${agora.app-id}")
    private String agoraAppId;
    @Value("${agora.app-certificate}")
    private String agoraCertificate;
    @Value("${livekit.api-key}")
    private String livekitKey;
    @Value("${livekit.api-secret}")
    private String livekitSecret;
    @Value("${livekit.url}")
    private String livekitUrl;

    public RtcToken issue(Vendor vendor, String channelName, long userId) {
        if (vendor == Vendor.LIVEKIT) {
            Map<String, Object> grant = new LinkedHashMap<>();
            grant.put("roomJoin", true);
            grant.put("room", channelName);
            grant.put("canPublish", true);
            grant.put("canSubscribe", true);
            grant.put("canPublishData", true);
            Date now = new Date();
            String token = Jwts.builder()
                    .setIssuer(livekitKey)
                    .setSubject(String.valueOf(userId))
                    .claim("video", grant)
                    .setNotBefore(now)
                    .setExpiration(new Date(now.getTime() + LIVEKIT_TTL_SECONDS * 1000))
                    .signWith(Keys.hmacShaKeyFor(livekitSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                    .compact();
            return new RtcToken(vendor, token, channelName, userId, null, livekitUrl);
        }
        String token = new RtcTokenBuilder2().buildTokenWithUid(agoraAppId, agoraCertificate, channelName, (int) userId,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER, AGORA_TTL_SECONDS, AGORA_TTL_SECONDS);
        return new RtcToken(vendor, token, channelName, userId, agoraAppId, null);
    }
}

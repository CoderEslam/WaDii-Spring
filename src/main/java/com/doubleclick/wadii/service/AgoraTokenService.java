package com.doubleclick.wadii.service;

import io.agora.media.AccessToken2;
import io.agora.media.RtcTokenBuilder2;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgoraTokenService {

    private static final Logger logger = LoggerFactory.getLogger(AgoraTokenService.class);
    private static final int TOKEN_EXPIRE_SECONDS = 360000; // 1 hour

    @Value("${agora.app-id}")
    private String appId;

    @Value("${agora.app-certificate}")
    private String appCertificate;

    private final RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

    @PostConstruct
    public void init() {
        if (appId == null || appId.isEmpty() || appCertificate == null || appCertificate.isEmpty()) {
            logger.error("Agora app id/certificate is not configured.");
            throw new IllegalArgumentException("Agora app id/certificate is not configured.");
        }
    }

    public String getAppId() {
        return appId;
    }

    public String buildRtcToken(String channelName, int uid) {
        logger.info("AppId={}", appId);
        logger.info("Channel={}", channelName);
        logger.info("Uid={}", uid);

        String token = tokenBuilder.buildTokenWithUid(
                appId,
                appCertificate,
                channelName,
                uid,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                TOKEN_EXPIRE_SECONDS,
                TOKEN_EXPIRE_SECONDS
        );

        logger.info("ESLAM=1=Token={}", token);

        return token;
//        return tokenBuilder.buildTokenWithUid(
//                appId,
//                appCertificate,
//                channelName,
//                uid,
//                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
//                TOKEN_EXPIRE_SECONDS,
//                TOKEN_EXPIRE_SECONDS
//        );
    }

    public String buildToken(String appId, String appCertificate, String userId, int expire) {
        AccessToken2 accessToken = new AccessToken2(appId, appCertificate, expire);
        AccessToken2.Service serviceRtm = new AccessToken2.ServiceRtm(userId);

        serviceRtm.addPrivilegeRtm(AccessToken2.PrivilegeRtm.PRIVILEGE_LOGIN, expire);
        accessToken.addService(serviceRtm);

        try {
            return accessToken.build();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}

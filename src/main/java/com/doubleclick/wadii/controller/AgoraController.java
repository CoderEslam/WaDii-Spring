package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.AgoraTokenRequest;
import com.doubleclick.wadii.dto.AgoraTokenResponse;
import com.doubleclick.wadii.dto.CallSignal;
import com.doubleclick.wadii.service.AgoraTokenService;
import com.doubleclick.wadii.service.CallSignalingService;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/agora")
public class AgoraController {

    private final AgoraTokenService agoraTokenService;
    private final CallSignalingService callSignalingService;
    private final UserRepository userRepository;

    @PostMapping("/token")
    public ResponseEntity<Response<AgoraTokenResponse>> token(Authentication authentication, @RequestBody AgoraTokenRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        Long authenticatedUserId = userOptional.get().getId();

        if (request.getChannelName() == null || request.getChannelName().isBlank()) {
            return Response.response(null, "channelName is required", ResponseType.ERROR);
        }
        if (request.getUid() != authenticatedUserId.intValue()) {
            return Response.response(null, "uid does not match the authenticated user", ResponseType.FORBIDDEN);
        }
        if (!isChannelMember(request.getChannelName(), authenticatedUserId)) {
            return Response.response(null, "you are not a member of this channel", ResponseType.FORBIDDEN);
        }

        String token = agoraTokenService.buildRtcToken(request.getChannelName(), request.getUid());
        AgoraTokenResponse response = new AgoraTokenResponse(token, agoraTokenService.getAppId());
        return Response.response(response, "Agora token generated", ResponseType.SUCCESS);
    }

    @PostMapping("/reject")
    public ResponseEntity<Response<Void>> reject(Authentication authentication, @RequestBody CallSignal request) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        Long authenticatedUserId = userOptional.get().getId();

        if (request.getChannelName() == null || request.getChannelName().isBlank()) {
            return Response.response(null, "channelName is required", ResponseType.ERROR);
        }
        if (request.getToUserId() == null) {
            return Response.response(null, "toUserId is required", ResponseType.ERROR);
        }
        if (!isChannelMember(request.getChannelName(), authenticatedUserId)) {
            return Response.response(null, "you are not a member of this channel", ResponseType.FORBIDDEN);
        }

        // never trust the client-supplied sender id — it must match the authenticated user
        request.setFromUserId(authenticatedUserId);
        callSignalingService.reject(request);
        return Response.response(null, "Call rejected", ResponseType.SUCCESS);
    }

    // channelName is built client-side as the two participant ids sorted and joined with "_", e.g. "3_7"
    private boolean isChannelMember(String channelName, Long userId) {
        String[] parts = channelName.split("_");
        if (parts.length != 2) return false;
        try {
            long a = Long.parseLong(parts[0]);
            long b = Long.parseLong(parts[1]);
            return a == userId || b == userId;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.LiveKitTokenRequest;
import com.doubleclick.wadii.dto.LiveKitTokenResponse;
import com.doubleclick.wadii.service.LiveKitTokenService;
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
@RequestMapping("/livekit")
public class LiveKitController {

    private final LiveKitTokenService liveKitTokenService;
    private final UserRepository userRepository;

    @PostMapping("/token")
    public ResponseEntity<Response<LiveKitTokenResponse>> token(Authentication authentication, @RequestBody LiveKitTokenRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        Long authenticatedUserId = userOptional.get().getId();

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            return Response.response(null, "roomName is required", ResponseType.ERROR);
        }
        if (request.getIdentity() == null || !request.getIdentity().equals(String.valueOf(authenticatedUserId))) {
            return Response.response(null, "identity does not match the authenticated user", ResponseType.FORBIDDEN);
        }
        if (!isRoomMember(request.getRoomName(), authenticatedUserId)) {
            return Response.response(null, "you are not a member of this room", ResponseType.FORBIDDEN);
        }

        String token = liveKitTokenService.buildAccessToken(request.getRoomName(), request.getIdentity(), request.getParticipantName());
        LiveKitTokenResponse response = new LiveKitTokenResponse(liveKitTokenService.getUrl(), token);
        System.out.println(request);
        System.out.println(response);
        return Response.response(response, "LiveKit token generated", ResponseType.SUCCESS);
    }

    // roomName is built client-side as the two participant ids sorted and joined with "_", e.g. "3_7"
    private boolean isRoomMember(String roomName, Long userId) {
        String[] parts = roomName.split("_");
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

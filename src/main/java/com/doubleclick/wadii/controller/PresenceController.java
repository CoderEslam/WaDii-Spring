package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import com.doubleclick.wadii.websocket.PresenceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceRegistry registry;

    /** Check whether specific users are online. GET /presence?userIds=1,2,3 */
    @GetMapping
    public ResponseEntity<Response<Map<Long, Boolean>>> checkPresence(
            @RequestParam List<Long> userIds) {
        Map<Long, Boolean> result = userIds.stream()
                .collect(Collectors.toMap(id -> id, registry::isOnline));
        return Response.response(result, "Presence status", ResponseType.SUCCESS);
    }

    /** Get all currently connected user IDs. GET /presence/online */
    @GetMapping("/online")
    public ResponseEntity<Response<Set<Long>>> getOnlineUsers() {
        return Response.response(registry.getOnlineUserIds(), "Online users", ResponseType.SUCCESS);
    }
}

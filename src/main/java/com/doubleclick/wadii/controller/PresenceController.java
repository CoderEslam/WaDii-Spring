package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.service.ChatAccessService;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import com.doubleclick.wadii.websocket.PresenceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Presence is only visible between users connected through an accepted order;
 * anyone else always shows as offline.
 */
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceRegistry registry;
    private final CurrentUserService currentUserService;
    private final ChatAccessService chatAccessService;

    /** Check whether specific users are online. GET /presence?userIds=1,2,3 */
    @GetMapping
    public ResponseEntity<Response<Map<Long, Boolean>>> checkPresence(Authentication authentication,
                                                                       @RequestParam List<Long> userIds) {
        User caller = currentUserService.user(authentication);
        Set<Long> visible = chatAccessService.counterparts(caller.getId());
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : userIds) {
            result.put(id, (visible.contains(id) || id.equals(caller.getId())) && registry.isOnline(id));
        }
        return Response.response(result, "Presence status", ResponseType.SUCCESS);
    }

    /** Online users among the ones the caller is connected to. GET /presence/online */
    @GetMapping("/online")
    public ResponseEntity<Response<Set<Long>>> getOnlineUsers(Authentication authentication) {
        User caller = currentUserService.user(authentication);
        Set<Long> online = new HashSet<>(registry.getOnlineUserIds());
        online.retainAll(chatAccessService.counterparts(caller.getId()));
        return Response.response(online, "Online users", ResponseType.SUCCESS);
    }
}

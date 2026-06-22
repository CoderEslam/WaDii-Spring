package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.UserNotificationDto;
import com.doubleclick.wadii.entities.UserNotification;
import com.doubleclick.wadii.repository.UserNotificationRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/notifications")
public class UserNotificationController extends Controller<UserNotification, UserNotificationDto, Long> {

    private final UserNotificationRepository userNotificationRepository;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Response<UserNotification>> show(Long id) {
        return userNotificationRepository.findById(id)
                .map(notification -> Response.response(notification, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no notification with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<UserNotification>> insert(Authentication authentication, @RequestBody UserNotificationDto dto) {
        if (dto.isNotEmpty()) {
            Optional<User> userOptional = userRepository.findById(dto.getUserId());
            if (userOptional.isEmpty()) {
                return Response.response(null, "there is no user with this id : " + dto.getUserId(), ResponseType.NOT_FOUND);
            }
            UserNotification notification = new UserNotification();
            notification.setTitle(dto.getTitle());
            notification.setBody(dto.getBody());
            notification.setType(dto.getType());
            notification.setUser(userOptional.get());
            notification = userNotificationRepository.save(notification);
            return Response.response(notification, "Notification sent successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "title, body, or userId is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<UserNotification>> update(@RequestBody UserNotificationDto dto) {
        if (dto.getId() == null) {
            return Response.response(null, "notification id is required", ResponseType.ERROR);
        }
        Optional<UserNotification> notificationOptional = userNotificationRepository.findById(dto.getId());
        if (notificationOptional.isEmpty()) {
            return Response.response(null, "there is no notification with this id : " + dto.getId(), ResponseType.NOT_FOUND);
        }
        UserNotification notification = notificationOptional.get();
        if (dto.getTitle() != null && !dto.getTitle().trim().isEmpty()) {
            notification.setTitle(dto.getTitle());
        }
        if (dto.getBody() != null && !dto.getBody().trim().isEmpty()) {
            notification.setBody(dto.getBody());
        }
        if (dto.getType() != null && !dto.getType().trim().isEmpty()) {
            notification.setType(dto.getType());
        }
        notification = userNotificationRepository.save(notification);
        return Response.response(notification, "Notification updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<UserNotification>> delete(Long id) {
        Optional<UserNotification> notificationOptional = userNotificationRepository.findById(id);
        if (notificationOptional.isPresent()) {
            userNotificationRepository.deleteById(id);
            return Response.response(null, "notification deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no notification with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<UserNotification>>> readAll() {
        return Response.response(userNotificationRepository.findAll(), "All notifications", ResponseType.SUCCESS);
    }

    @GetMapping("/my")
    public ResponseEntity<Response<List<UserNotification>>> getMyNotifications(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        List<UserNotification> notifications = userNotificationRepository.findByUserIdOrderByCreatedAtDesc(userOptional.get().getId());
        return Response.response(notifications, "My notifications", ResponseType.SUCCESS);
    }

    @GetMapping("/my/unread")
    public ResponseEntity<Response<List<UserNotification>>> getUnreadNotifications(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        List<UserNotification> notifications = userNotificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(userOptional.get().getId(), false);
        return Response.response(notifications, "Unread notifications", ResponseType.SUCCESS);
    }

    @GetMapping("/my/unread-count")
    public ResponseEntity<Response<Long>> getUnreadCount(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        long count = userNotificationRepository.countByUserIdAndIsRead(userOptional.get().getId(), false);
        return Response.response(count, "Unread count", ResponseType.SUCCESS);
    }

    @PostMapping("/mark-read/{id}")
    public ResponseEntity<Response<UserNotification>> markAsRead(@PathVariable Long id) {
        Optional<UserNotification> notificationOptional = userNotificationRepository.findById(id);
        if (notificationOptional.isEmpty()) {
            return Response.response(null, "there is no notification with this id : " + id, ResponseType.NOT_FOUND);
        }
        UserNotification notification = notificationOptional.get();
        notification.setIsRead(true);
        notification = userNotificationRepository.save(notification);
        return Response.response(notification, "Notification marked as read", ResponseType.SUCCESS);
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Response<List<UserNotification>>> markAllAsRead(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        List<UserNotification> unread = userNotificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(userOptional.get().getId(), false);
        unread.forEach(n -> n.setIsRead(true));
        unread = userNotificationRepository.saveAll(unread);
        return Response.response(unread, "All notifications marked as read", ResponseType.SUCCESS);
    }
}

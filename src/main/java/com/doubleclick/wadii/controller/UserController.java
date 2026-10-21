package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.UserDto;
import com.doubleclick.wadii.entities.City;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.CityRepository;
import com.doubleclick.wadii.repository.MessageRepository;
import com.doubleclick.wadii.service.ChatAccessService;
import com.doubleclick.wadii.service.FileStorageService;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import com.doubleclick.wadii.websocket.UserPresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController extends Controller<User, UserDto, Long> {

    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final MessageRepository messageRepository;
    private final UserPresenceTracker userPresenceTracker;
    private final CurrentUserService currentUserService;
    private final ChatAccessService chatAccessService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public ResponseEntity<Response<User>> show(Long id) {
        // Own account, admins, or the user connected through an accepted order.
        User caller = currentUserService.user();
        if (!currentUserService.isAdmin(caller) && !caller.getId().equals(id)
                && !chatAccessService.canCommunicate(caller.getId(), id)) {
            throw ApiException.forbidden("You are not allowed to view this user");
        }
        return userRepository.findById(id)
                .map(user -> Response.response(user, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no user with this id : " + id, ResponseType.NOT_FOUND));
    }

    /** Admin only. Creates a USER (0) or ADMIN (2); providers are created with /providers/create-by-admin. */
    @Override
    public ResponseEntity<Response<User>> insert(Authentication authentication, @RequestBody UserDto userDto) {
        currentUserService.admin(authentication);
        if (userDto.getEmail() == null || userDto.getEmail().trim().isEmpty()) {
            throw ApiException.badRequest("email is required");
        }
        if (userDto.getPassword() == null || userDto.getPassword().length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }
        if (userDto.getUserType() == null || (userDto.getUserType() != 0 && userDto.getUserType() != 2)) {
            throw ApiException.badRequest("userType must be 0 (user) or 2 (admin); create providers with /providers/create-by-admin");
        }
        if (userRepository.findByEmail(userDto.getEmail().trim()).isPresent()) {
            throw ApiException.conflict("User already exists with this email = " + userDto.getEmail());
        }
        User user = new User();
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setEmail(userDto.getEmail().trim());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setPhone(userDto.getPhone());
        user.setImage("download.jpeg");
        user.setBackgroundImage("download.jpeg");
        user.setRole(userDto.getUserType() == 2 ? Role.ADMIN : Role.USER);
        if (userDto.getCityId() != null) {
            City city = cityRepository.findById(userDto.getCityId())
                    .orElseThrow(() -> ApiException.badRequest("this city id not exist with id = " + userDto.getCityId()));
            user.setCity(city);
        }
        user = userRepository.save(user);
        return Response.response(user, "User saved successfully", ResponseType.SUCCESS);
    }

    /** The caller can update their own account; admins can update anyone (except another user's FCM token). */
    @Override
    public ResponseEntity<Response<User>> update(Authentication authentication, UserDto userDto) {
        User caller = currentUserService.user(authentication);
        if (userDto.getId() == null) {
            throw ApiException.badRequest("user id is required");
        }
        currentUserService.requireSelfOrAdmin(caller, userDto.getId());
        User user = userRepository.findById(userDto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no user with this id : " + userDto.getId()));
        if (userDto.getFirstName() != null) user.setFirstName(userDto.getFirstName());
        if (userDto.getLastName() != null) user.setLastName(userDto.getLastName());
        if (userDto.getPhone() != null) user.setPhone(userDto.getPhone());
        if (userDto.getCityId() != null) {
            City city = cityRepository.findById(userDto.getCityId())
                    .orElseThrow(() -> ApiException.badRequest("this city id not exist with id = " + userDto.getCityId()));
            user.setCity(city);
        }
        // The FCM token belongs to the device of the authenticated user.
        if (userDto.getFcmToken() != null && caller.getId().equals(user.getId())) {
            user.setFcmToken(userDto.getFcmToken());
        }
        user = userRepository.save(user);
        return Response.response(user, "User saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        User caller = currentUserService.user(authentication);
        currentUserService.requireSelfOrAdmin(caller, id);
        if (!userRepository.existsById(id)) {
            return Response.response(false, "there is no user with this id : " + id, ResponseType.NOT_FOUND);
        }
        userRepository.deleteById(id);
        return Response.response(true, "user deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<User>>> readAll() {
        currentUserService.admin();
        return Response.response(userRepository.findAll(), "All users", ResponseType.SUCCESS);
    }

    @GetMapping("/me")
    public ResponseEntity<Response<User>> getMyProfile(Authentication authentication) {
        return Response.response(currentUserService.user(authentication), "Done", ResponseType.SUCCESS);
    }

    @PostMapping("/upload-image")
    public ResponseEntity<Response<User>> uploadImage(Authentication authentication, @RequestParam("file") MultipartFile file) {
        User user = currentUserService.user(authentication);
        user.setImage(fileStorageService.storePublicImage(file));
        user = userRepository.save(user);
        return Response.response(user, "File uploaded successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/upload-background-image")
    public ResponseEntity<Response<User>> uploadBackgroundImage(Authentication authentication, @RequestParam("file") MultipartFile file) {
        User user = currentUserService.user(authentication);
        user.setBackgroundImage(fileStorageService.storePublicImage(file));
        user = userRepository.save(user);
        return Response.response(user, "File uploaded successfully", ResponseType.SUCCESS);
    }

    /** Presence is only visible between users connected through an accepted order. */
    @GetMapping("/{id}/online")
    public ResponseEntity<Response<Boolean>> isOnline(Authentication authentication, @PathVariable Long id) {
        User caller = currentUserService.user(authentication);
        boolean visible = caller.getId().equals(id) || chatAccessService.canCommunicate(caller.getId(), id);
        return Response.response(visible && userPresenceTracker.isOnline(id), "Online status", ResponseType.SUCCESS);
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<Response<Long>> getUnreadCount(Authentication authentication) {
        User user = currentUserService.user(authentication);
        return Response.response(messageRepository.countUnreadMessages(user.getId()), "Unread count", ResponseType.SUCCESS);
    }

    /** Public profile / background images only. Verification documents are never served from here. */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        // Provider documents uploaded before the private store existed were named "<epochMillis>_<name>".
        if (filename.matches("\\d{10,}_.*")) {
            throw ApiException.notFound("File not found");
        }
        Resource resource = fileStorageService.loadPublic(filename);
        String contentType = null;
        try {
            contentType = Files.probeContentType(resource.getFile().toPath());
        } catch (IOException ignored) {
            // fall back to octet-stream
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}

package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.config.JwtUtil;
import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.UserDto;
import com.doubleclick.wadii.entities.City;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.CityRepository;
import com.doubleclick.wadii.repository.MessageRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import com.doubleclick.wadii.websocket.UserPresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController extends Controller<User, UserDto, Long> {

    private static final String UPLOAD_DIR = "uploads/";
    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final MessageRepository messageRepository;
    private final JwtUtil jwtUtil;
    private final UserPresenceTracker userPresenceTracker;


    @Override
    public ResponseEntity<Response<User>> show(Long id) {
        return userRepository.findById(id)
                .map(user -> Response.response(user, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no user with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<User>> insert(Authentication authentication, @RequestBody UserDto userDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        Optional<City> city = cityRepository.findById(userDto.getCityId());
        if (userOptional.isPresent() && city.isPresent()) {
            if (userOptional.get().getRole() == Role.ADMIN) {
                User user = new User();
                user.setFirstName(userDto.getFirstName());
                user.setLastName(userDto.getLastName());
                user.setEmail(userDto.getEmail());
                user.setPassword(userDto.getPassword());
                user.setPhone(userDto.getPhone());
                user.setCity(city.get());
                if (userDto.getUserType() == 0) {
                    user.setRole(Role.USER);
                } else {
                    user.setRole(Role.PROVIDER);
                }
                user = userRepository.save(user);
                user.setToken(jwtUtil.generateToken(user.getEmail(), user.getId()));
                user = userRepository.save(user);
                return Response.response(user, "User saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "You are not admin to create user", ResponseType.ERROR);
            }
        } else {
            return Response.response(null, "Invalid user", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<User>> update(Authentication authentication, UserDto userDto) {
        Optional<User> userOptional = userRepository.findById(userDto.getId());
        Optional<City> cityOptional = cityRepository.findById(userDto.getCityId());
        if (userOptional.isPresent() && cityOptional.isPresent()) {
            User user = userOptional.get();
            user.setFirstName(userDto.getFirstName());
            user.setLastName(userDto.getLastName());
            user.setFcmToken(userDto.getFcmToken());
            user.setPhone(userDto.getPhone());
//            user.setCity(cityOptional.get());
            user = userRepository.save(user);
            return Response.response(user, "User saved successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "Invalid user", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<User>> delete(Authentication authentication, Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            userRepository.deleteById(id);
            return Response.response(null, "user deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no user with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<User>>> readAll() {
        return Response.response(userRepository.findAll(), "All cities", ResponseType.SUCCESS);
    }

    @GetMapping("/me")
    public ResponseEntity<Response<User>> getMyProfile(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(user -> Response.response(user, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "User not found", ResponseType.NOT_FOUND));
    }

    @PostMapping("/upload-image")
    public ResponseEntity<Response<User>> uploadImage(@RequestHeader("Authorization") String authHeader, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Response.response(null, "No file selected.", ResponseType.ERROR);
        }
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOptional = userRepository.findByEmail(jwtUtil.extractEmail(token));
            if (userOptional.isPresent()) {
                // Define a base directory to save the uploaded images
                String uploadDir = System.getProperty("user.dir") + "/uploads";
                // Create directory if it doesn't exist
                File dir = new File(uploadDir);
                if (!dir.exists()) {
                    dir.mkdirs(); // Create all non-existing parent dirs
                }

                // Full path to save the file
                String filePath = uploadDir + File.separator + file.getOriginalFilename();
                File dest = new File(filePath);

                // Save the file
                file.transferTo(dest);
                User user = userOptional.get();
                user.setImage(file.getOriginalFilename());
                user = userRepository.save(user);
                return Response.response(user, "File uploaded successfully: ", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "Invalid user.", ResponseType.UNAUTHORIZED);
            }
        } catch (IOException e) {
            return Response.response(null, "Invalid user. " + e.getMessage(), ResponseType.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/upload-background-image")
    public ResponseEntity<Response<User>> uploadBackgroundImage(@RequestHeader("Authorization") String authHeader, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Response.response(null, "No file selected.", ResponseType.ERROR);
        }
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOptional = userRepository.findByEmail(jwtUtil.extractEmail(token));
            if (userOptional.isPresent()) {
                // Define a base directory to save the uploaded images
                String uploadDir = System.getProperty("user.dir") + "/uploads";
                // Create directory if it doesn't exist
                File dir = new File(uploadDir);
                if (!dir.exists()) {
                    dir.mkdirs(); // Create all non-existing parent dirs
                }

                // Full path to save the file
                String filePath = uploadDir + File.separator + file.getOriginalFilename();
                File dest = new File(filePath);

                // Save the file
                file.transferTo(dest);
                User user = userOptional.get();
                user.setBackgroundImage(file.getOriginalFilename());
                user = userRepository.save(user);
                return Response.response(user, "File uploaded successfully: ", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "Invalid user.", ResponseType.UNAUTHORIZED);
            }
        } catch (IOException e) {
            return Response.response(null, "Invalid user. " + e.getMessage(), ResponseType.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}/online")
    public ResponseEntity<Response<Boolean>> isOnline(@PathVariable Long id) {
        return Response.response(userPresenceTracker.isOnline(id), "Online status", ResponseType.SUCCESS);
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<Response<Long>> getUnreadCount(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(user -> Response.response(messageRepository.countUnreadMessages(user.getId()), "Unread count", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "User not found", ResponseType.NOT_FOUND));
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Try to determine content type (jpeg, png, etc.)
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

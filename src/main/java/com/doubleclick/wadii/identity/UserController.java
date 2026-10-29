package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.catalog.CityRepository;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.dto.UpdateProfileRequest;
import com.doubleclick.wadii.identity.dto.UserView;
import com.doubleclick.wadii.storage.FileCategory;
import com.doubleclick.wadii.storage.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The caller's own account. There is no endpoint that takes another user's id. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final FileStorageService storage;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<UserView>> me(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(UserView.of(load(me)));
    }

    @PatchMapping
    @Transactional
    public ResponseEntity<ApiResponse<UserView>> update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        User user = load(me);
        if (request.firstName() != null) {
            if (request.firstName().isBlank()) throw BusinessException.validation("firstName must not be blank");
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) user.setLastName(request.lastName().trim());
        if (request.phone() != null) user.setPhone(request.phone().trim());
        if (request.cityId() != null) {
            user.setCity(cityRepository.findById(request.cityId())
                    .orElseThrow(() -> BusinessException.validation("cityId does not exist")));
        }
        if (request.fcmToken() != null) user.setFcmToken(request.fcmToken().isBlank() ? null : request.fcmToken());
        return ApiResponse.ok(UserView.of(user), "Profile updated");
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<UserView>> uploadImage(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file) {
        User user = load(me);
        user.setImage(storage.store(file, FileCategory.PROFILE_IMAGE).key());
        return ApiResponse.ok(UserView.of(user), "Image uploaded");
    }

    @PostMapping(value = "/background-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<UserView>> uploadBackground(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file) {
        User user = load(me);
        user.setBackgroundImage(storage.store(file, FileCategory.PROFILE_IMAGE).key());
        return ApiResponse.ok(UserView.of(user), "Background image uploaded");
    }

    private User load(AuthUser me) {
        return userRepository.findById(me.id()).orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHENTICATED));
    }
}

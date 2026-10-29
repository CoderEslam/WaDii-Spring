package com.doubleclick.wadii.rating;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.rating.RatingService.RatingRequest;
import com.doubleclick.wadii.rating.RatingService.RatingView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping("/orders/{orderId}/rating")
    public ResponseEntity<ApiResponse<RatingView>> rate(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                        @Valid @RequestBody RatingRequest request) {
        return ApiResponse.created(ratingService.rate(me, orderId, request), "Thanks for rating");
    }

    @GetMapping("/orders/{orderId}/rating")
    public ResponseEntity<ApiResponse<RatingView>> forOrder(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(ratingService.forOrder(me, orderId));
    }

    @PatchMapping("/ratings/{id}")
    public ResponseEntity<ApiResponse<RatingView>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                          @Valid @RequestBody RatingRequest request) {
        return ApiResponse.ok(ratingService.update(me, id, request), "Rating updated");
    }

    @DeleteMapping("/ratings/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        ratingService.delete(me, id);
        return ApiResponse.ok(null, "Rating deleted");
    }

    @GetMapping("/providers/{providerId}/ratings")
    public ResponseEntity<ApiResponse<PageResponse<RatingView>>> forProvider(@PathVariable Long providerId,
                                                                             @RequestParam(required = false) Integer page,
                                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(ratingService.forProvider(providerId, page, size));
    }
}

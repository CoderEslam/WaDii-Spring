package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.provider.dto.ProviderDtos.OfferRequest;
import com.doubleclick.wadii.provider.dto.ProviderDtos.OfferView;
import com.doubleclick.wadii.provider.dto.ProviderDtos.ProviderPublicView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class SocialController {

    private final SocialService socialService;

    @PostMapping("/providers/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        socialService.follow(me, id);
        return ApiResponse.ok(null, "Following");
    }

    @DeleteMapping("/providers/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        socialService.unfollow(me, id);
        return ApiResponse.ok(null, "Unfollowed");
    }

    @GetMapping("/users/me/following")
    public ResponseEntity<ApiResponse<PageResponse<ProviderPublicView>>> following(@AuthenticationPrincipal AuthUser me,
                                                                                   @RequestParam(required = false) Integer page,
                                                                                   @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.following(me, page, size));
    }

    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<PageResponse<OfferView>>> offers(@AuthenticationPrincipal AuthUser me,
                                                                       @RequestParam(required = false) Long serviceId,
                                                                       @RequestParam(required = false) Long providerId,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.currentOffers(me, serviceId, providerId, page, size));
    }

    @PostMapping("/providers/me/offers")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<OfferView>> createOffer(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody OfferRequest request) {
        return ApiResponse.created(socialService.createOffer(me, request), "Offer created");
    }

    @PatchMapping("/providers/me/offers/{offerId}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<OfferView>> updateOffer(@AuthenticationPrincipal AuthUser me, @PathVariable Long offerId,
                                                              @Valid @RequestBody OfferRequest request) {
        return ApiResponse.ok(socialService.updateOffer(me, offerId, request), "Offer updated");
    }

    @DeleteMapping("/providers/me/offers/{offerId}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<Void>> deleteOffer(@AuthenticationPrincipal AuthUser me, @PathVariable Long offerId) {
        socialService.deleteOffer(me, offerId);
        return ApiResponse.ok(null, "Offer deleted");
    }

    @PostMapping("/offers/{offerId}/save")
    public ResponseEntity<ApiResponse<Void>> save(@AuthenticationPrincipal AuthUser me, @PathVariable Long offerId) {
        socialService.saveOffer(me, offerId);
        return ApiResponse.ok(null, "Offer saved");
    }

    @DeleteMapping("/offers/{offerId}/save")
    public ResponseEntity<ApiResponse<Void>> unsave(@AuthenticationPrincipal AuthUser me, @PathVariable Long offerId) {
        socialService.unsaveOffer(me, offerId);
        return ApiResponse.ok(null, "Offer removed from saved");
    }

    @GetMapping("/users/me/saved-offers")
    public ResponseEntity<ApiResponse<PageResponse<OfferView>>> saved(@AuthenticationPrincipal AuthUser me,
                                                                      @RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.savedOffers(me, page, size));
    }
}

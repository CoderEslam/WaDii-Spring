package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.offer.OfferViews.OfferInput;
import com.doubleclick.wadii.offer.OfferViews.ProviderOfferView;
import com.doubleclick.wadii.request.RequestTarget;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provider Mode: incoming requests and offers. */
@RestController
@RequestMapping("/api/v1/provider")
@RequiredArgsConstructor
public class ProviderOfferController {

    private final ProviderInboxService inbox;
    private final OfferService offers;

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<PageResponse<ProviderInboxService.InboxRow>>> inbox(
            @AuthenticationPrincipal AuthUser me, @RequestParam(required = false) RequestTarget.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(inbox.inbox(me, status, page, size));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<ProviderInboxService.RequestView>> request(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(inbox.view(me, id));
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<ApiResponse<Void>> decline(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        inbox.decline(me, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/requests/{id}/offer")
    public ResponseEntity<ApiResponse<ProviderOfferView>> submit(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @Valid @RequestBody OfferInput body) {
        return ApiResponse.created(offers.submit(me, id, body), "Offer sent");
    }

    @PutMapping("/offers/{id}")
    public ResponseEntity<ApiResponse<ProviderOfferView>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @Valid @RequestBody OfferInput body) {
        return ApiResponse.ok(offers.update(me, id, body));
    }

    @PostMapping("/offers/{id}/withdraw")
    public ResponseEntity<ApiResponse<ProviderOfferView>> withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(offers.withdraw(me, id));
    }

    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<PageResponse<ProviderOfferView>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                            @RequestParam(required = false) Offer.Status status,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(offers.mine(me, status, page, size));
    }
}

package com.doubleclick.wadii.response;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.response.dto.ResponseDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class ResponseController {

    private final ResponseService responseService;

    @PostMapping("/responses")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ResponseView>> submit(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SubmitResponseRequest request) {
        return ApiResponse.created(responseService.submit(me, request), "Offer submitted");
    }

    @PatchMapping("/responses/{id}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ResponseView>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                            @Valid @RequestBody UpdateResponseRequest request) {
        return ApiResponse.ok(responseService.update(me, id, request), "Offer updated");
    }

    @PostMapping("/responses/{id}/withdraw")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ResponseView>> withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(responseService.withdraw(me, id), "Offer withdrawn");
    }

    @PostMapping("/responses/{id}/accept")
    public ResponseEntity<ApiResponse<AcceptResult>> accept(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(responseService.accept(me, id, null), "Offer accepted");
    }

    @PostMapping("/responses/{id}/decline")
    public ResponseEntity<ApiResponse<ResponseView>> decline(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(responseService.decline(me, id), "Offer declined");
    }

    @GetMapping("/responses/{id}")
    public ResponseEntity<ApiResponse<ResponseView>> get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(responseService.get(me, id));
    }

    /** Provider: offers I sent. Customer: offers received on my orders. */
    @GetMapping("/responses/mine")
    public ResponseEntity<ApiResponse<PageResponse<ResponseView>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                        @RequestParam(required = false) Integer page,
                                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(responseService.mine(me, page, size));
    }

    @GetMapping("/orders/{orderId}/responses")
    public ResponseEntity<ApiResponse<PageResponse<ResponseView>>> forOrder(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                                            @RequestParam(defaultValue = "RECOMMENDED") SortMode sort,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(responseService.forOrder(me, orderId, sort, page, size));
    }

    @GetMapping("/orders/{orderId}/my-response")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ResponseView>> mineForOrder(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(responseService.mineForOrder(me, orderId));
    }
}

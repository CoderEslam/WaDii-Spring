package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.dispute.DisputeRecorder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService service;

    @GetMapping("/warranties")
    public ResponseEntity<ApiResponse<List<WarrantyService.WarrantyView>>> list(@AuthenticationPrincipal AuthUser me,
                                                                                @RequestParam(defaultValue = "customer") String as) {
        return ApiResponse.ok(service.list(me, "provider".equalsIgnoreCase(as)));
    }

    @PostMapping("/warranties/{id}/claims")
    public ResponseEntity<ApiResponse<WarrantyService.ClaimView>> claim(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                        @Valid @RequestBody WarrantyService.ClaimInput body) {
        return ApiResponse.created(service.claim(me, id, body), "Claim filed");
    }

    @GetMapping("/warranty-claims")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyService.ClaimView>>> claims(@AuthenticationPrincipal AuthUser me,
                                                                                      @RequestParam(defaultValue = "customer") String as,
                                                                                      @RequestParam(required = false) Integer page,
                                                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.claims(me, "provider".equalsIgnoreCase(as), page, size));
    }

    @PostMapping("/warranty-claims/{id}/respond")
    public ResponseEntity<ApiResponse<WarrantyService.ClaimView>> respond(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                          @Valid @RequestBody WarrantyService.RespondInput body) {
        return ApiResponse.ok(service.respond(me, id, body));
    }

    @PostMapping("/warranty-claims/{id}/resolve")
    public ResponseEntity<ApiResponse<WarrantyService.ClaimView>> resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(service.resolve(me, id));
    }

    @PostMapping("/warranty-claims/{id}/escalate")
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> escalate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                      @Valid @RequestBody WarrantyService.EscalateInput body) {
        return ApiResponse.created(service.escalate(me, id, body), "Escalated to support");
    }
}

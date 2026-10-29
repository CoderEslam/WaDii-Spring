package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.dispute.DisputeService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping("/orders/{orderId}/disputes")
    public ResponseEntity<ApiResponse<DisputeView>> open(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                         @Valid @RequestBody OpenRequest request) {
        return ApiResponse.created(disputeService.open(me, orderId, request), "Dispute opened");
    }

    @GetMapping("/disputes/mine")
    public ResponseEntity<ApiResponse<PageResponse<DisputeView>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(disputeService.mine(me, page, size));
    }

    @GetMapping("/disputes/{id}")
    public ResponseEntity<ApiResponse<DisputeView>> get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(disputeService.get(me, id));
    }

    @PostMapping(value = "/disputes/{id}/evidence", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<ApiResponse<EvidenceView>> evidence(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                              @RequestParam EvidenceKind kind,
                                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                                              @RequestParam(required = false) Long messageId,
                                                              @RequestParam(required = false) Long paymentProofId,
                                                              @RequestParam(required = false) String note) {
        return ApiResponse.created(disputeService.addEvidence(me, id, kind, file, messageId, paymentProofId, note), "Evidence added");
    }

    @GetMapping("/dispute-evidence/{evidenceId}/file")
    public ResponseEntity<Resource> evidenceFile(@AuthenticationPrincipal AuthUser me, @PathVariable Long evidenceId) {
        return disputeService.evidenceFile(me, evidenceId);
    }

    @PostMapping("/disputes/{id}/cancel")
    public ResponseEntity<ApiResponse<DisputeView>> cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(disputeService.cancel(me, id), "Dispute canceled");
    }

    @GetMapping("/admin/disputes")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<PageResponse<DisputeView>>> adminList(@RequestParam(required = false) Set<DisputeStatus> status,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(disputeService.adminList(status, page, size));
    }

    @PostMapping("/admin/disputes/{id}/status")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<DisputeView>> status(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                           @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(disputeService.changeStatus(me, id, request), "Dispute updated");
    }

    @PostMapping("/admin/disputes/{id}/resolve")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<DisputeView>> resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                            @Valid @RequestBody ResolveRequest request) {
        return ApiResponse.ok(disputeService.resolve(me, id, request), "Dispute resolved");
    }

    @PostMapping("/admin/disputes/{id}/reject")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<DisputeView>> reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                           @RequestParam String resolution) {
        return ApiResponse.ok(disputeService.reject(me, id, resolution), "Dispute rejected");
    }
}

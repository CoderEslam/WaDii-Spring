package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.order.dto.OrderDtos.*;
import com.doubleclick.wadii.policy.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    @PreAuthorize(Permissions.CUSTOMER)
    public ResponseEntity<ApiResponse<OrderView>> create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created(orderService.create(me, request), "Order created");
    }

    @GetMapping("/orders/mine")
    public ResponseEntity<ApiResponse<PageResponse<OrderView>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                     @RequestParam(required = false) Set<OrderStatus> status,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.mine(me, status, page, size));
    }

    @GetMapping("/providers/me/orders")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<PageResponse<OrderView>>> forProvider(@AuthenticationPrincipal AuthUser me,
                                                                            @RequestParam(defaultValue = "ALL") String scope,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.forProvider(me, scope, page, size));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderView>> get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.get(me, id));
    }

    @PatchMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderView>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                         @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(me, id, request), "Order updated");
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<ApiResponse<CancelResult>> cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                            @Valid @RequestBody(required = false) CancelRequest request) {
        return ApiResponse.ok(orderService.cancel(me, id, request), "Order canceled");
    }

    @GetMapping("/orders/{id}/cancellation")
    public ResponseEntity<ApiResponse<CancellationView>> cancellation(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.cancellation(me, id));
    }

    @PostMapping("/orders/{id}/decline-invitation")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<Void>> declineInvitation(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        orderService.declineInvitation(me, id);
        return ApiResponse.ok(null, "Invitation declined");
    }

    @PostMapping("/orders/{id}/start")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<OrderView>> start(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.start(me, id), "Service started");
    }

    @PostMapping("/orders/{id}/mark-done")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<OrderView>> markDone(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.markDone(me, id), "Marked as done; waiting for customer confirmation");
    }

    @PostMapping("/orders/{id}/confirm-completion")
    public ResponseEntity<ApiResponse<OrderView>> confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.confirmCompletion(me, id), "Order completed");
    }

    @GetMapping("/orders/{id}/timeline")
    public ResponseEntity<ApiResponse<PageResponse<TimelineView>>> timeline(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                           @RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.timeline(me, id, page, size));
    }

    @GetMapping("/orders/{id}/policies")
    public ResponseEntity<ApiResponse<List<PolicyService.SnapshotView>>> policies(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.policies(me, id));
    }

    @PostMapping(value = "/orders/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentView>> addAttachment(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                     @RequestParam("file") MultipartFile file) {
        return ApiResponse.created(orderService.addAttachment(me, id, file), "Attachment uploaded");
    }

    @GetMapping("/orders/{id}/attachments")
    public ResponseEntity<ApiResponse<List<AttachmentView>>> attachments(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(orderService.attachments(me, id));
    }

    @GetMapping("/orders/{id}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> attachmentFile(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long attachmentId) {
        return orderService.attachmentFile(me, id, attachmentId);
    }

    @GetMapping("/admin/orders")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<OrderView>>> adminList(@RequestParam(required = false) Set<OrderStatus> status,
                                                                          @RequestParam(required = false) Integer page,
                                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.adminList(status, page, size));
    }
}

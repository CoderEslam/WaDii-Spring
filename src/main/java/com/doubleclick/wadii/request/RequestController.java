package com.doubleclick.wadii.request;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.order.AcceptanceService;
import com.doubleclick.wadii.order.OrderQueryService;
import com.doubleclick.wadii.request.RequestQueryService.RequestDetail;
import com.doubleclick.wadii.request.RequestQueryService.RequestSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

import java.time.Instant;

/** Customer Mode: parent requests, their items and offer decisions. */
@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService service;
    private final RequestQueryService query;
    private final AcceptanceService acceptance;
    private final OrderQueryService orders;

    public record AcceptInput(@NotNull Long lineId, Instant appointmentStart) {
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RequestDetail>> create(@AuthenticationPrincipal AuthUser me,
                                                             @Valid @RequestBody RequestService.RequestInput body) {
        return ApiResponse.created(query.detail(service.create(me, body)), "Request created");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RequestSummary>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                         @RequestParam(required = false) ServiceRequest.Status status,
                                                                         @RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(query.mine(me, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestDetail>> detail(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(query.detail(me, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestDetail>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                             @Valid @RequestBody RequestService.RequestInput body) {
        return ApiResponse.ok(query.detail(service.update(me, id, body)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestDetail>> cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                             @Valid @RequestBody(required = false) RequestService.CancelInput body) {
        return ApiResponse.ok(query.detail(service.cancel(me, id, body == null ? null : body.reason())));
    }

    @PostMapping("/{id}/items/{itemId}/cancel")
    public ResponseEntity<ApiResponse<RequestDetail>> cancelItem(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @PathVariable Long itemId) {
        return ApiResponse.ok(query.detail(service.cancelItem(me, id, itemId)));
    }

    @PostMapping("/{id}/items/{itemId}/accept")
    public ResponseEntity<ApiResponse<OrderQueryService.OrderDetail>> accept(@AuthenticationPrincipal AuthUser me,
                                                                            @PathVariable Long id, @PathVariable Long itemId,
                                                                            @Valid @RequestBody AcceptInput body) {
        var order = acceptance.accept(me, id, itemId, body.lineId(), body.appointmentStart());
        return ApiResponse.created(orders.detail(me, order.getId()), "Offer accepted");
    }

    @PostMapping("/{id}/lines/{lineId}/decline")
    public ResponseEntity<ApiResponse<RequestDetail>> decline(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                              @PathVariable Long lineId) {
        return ApiResponse.ok(query.detail(service.declineLine(me, id, lineId)));
    }
}

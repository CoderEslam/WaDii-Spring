package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.dispute.DisputeRecorder;
import com.doubleclick.wadii.order.OrderQueryService.ChargeView;
import com.doubleclick.wadii.order.OrderQueryService.EventView;
import com.doubleclick.wadii.order.OrderQueryService.OrderDetail;
import com.doubleclick.wadii.order.OrderQueryService.OrderSummary;
import com.doubleclick.wadii.order.OrderQueryService.RatingView;
import com.doubleclick.wadii.order.OrderService.CashInput;
import com.doubleclick.wadii.order.OrderService.ChargeInput;
import com.doubleclick.wadii.order.OrderService.ComplaintInput;
import com.doubleclick.wadii.order.OrderService.DescriptionInput;
import com.doubleclick.wadii.order.OrderService.NoteInput;
import com.doubleclick.wadii.order.OrderService.RatingInput;
import com.doubleclick.wadii.order.OrderService.ReasonInput;
import com.doubleclick.wadii.order.OrderService.RescheduleInput;
import com.doubleclick.wadii.order.OrderService.StatementInput;
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

/** Service Orders for both parties. Every action returns the refreshed order (or the dispute/charge it created). */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;
    private final OrderQueryService query;
    private final DisputeRecorder disputes;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummary>>> list(@AuthenticationPrincipal AuthUser me,
                                                                       @RequestParam(defaultValue = "customer") String as,
                                                                       @RequestParam(required = false) ServiceOrder.Status status,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(query.list(me, "provider".equalsIgnoreCase(as), status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetail>> detail(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(query.detail(me, id));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<EventView>>> events(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(query.events(me, id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<OrderDetail>> start(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.start(me, id);
        return detail(me, id);
    }

    @PostMapping("/{id}/mark-completed")
    public ResponseEntity<ApiResponse<OrderDetail>> markCompleted(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                  @Valid @RequestBody(required = false) NoteInput body) {
        service.markCompleted(me, id, body == null ? null : body.note());
        return detail(me, id);
    }

    @PostMapping("/{id}/confirm-completion")
    public ResponseEntity<ApiResponse<OrderDetail>> confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.confirmCompletion(me, id);
        return detail(me, id);
    }

    @PostMapping("/{id}/complain")
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> complain(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                      @Valid @RequestBody ComplaintInput body) {
        return ApiResponse.created(disputes.view(service.complain(me, id, body)), "Complaint opened");
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDetail>> cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                           @Valid @RequestBody ReasonInput body) {
        service.cancel(me, id, body.reason());
        return detail(me, id);
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<OrderDetail>> reschedule(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                               @Valid @RequestBody RescheduleInput body) {
        service.reschedule(me, id, body);
        return detail(me, id);
    }

    @PostMapping("/{id}/report-no-show")
    public ResponseEntity<ApiResponse<OrderDetail>> noShow(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.reportNoShow(me, id);
        return detail(me, id);
    }

    @PostMapping("/{id}/request-interruption")
    public ResponseEntity<ApiResponse<OrderDetail>> interruption(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @Valid @RequestBody ReasonInput body) {
        service.requestInterruption(me, id, body.reason());
        return detail(me, id);
    }

    @PostMapping("/{id}/interruption-statement")
    public ResponseEntity<ApiResponse<OrderDetail>> statement(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                              @Valid @RequestBody StatementInput body) {
        service.interruptionStatement(me, id, body);
        return detail(me, id);
    }

    @PostMapping("/{id}/accept-interruption")
    public ResponseEntity<ApiResponse<OrderDetail>> acceptInterruption(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.acceptInterruption(me, id);
        return detail(me, id);
    }

    @PostMapping("/{id}/dispute-interruption")
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> disputeInterruption(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                                 @Valid @RequestBody ComplaintInput body) {
        return ApiResponse.created(disputes.view(service.disputeInterruption(me, id, body)), "Dispute opened");
    }

    @PostMapping("/{id}/cash/report")
    public ResponseEntity<ApiResponse<OrderDetail>> reportCash(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                               @Valid @RequestBody CashInput body) {
        service.reportCash(me, id, body.amount());
        return detail(me, id);
    }

    @PostMapping("/{id}/cash/confirm")
    public ResponseEntity<ApiResponse<OrderDetail>> confirmCash(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.confirmCash(me, id);
        return detail(me, id);
    }

    @PostMapping("/{id}/cash/dispute")
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> disputeCash(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                         @Valid @RequestBody DescriptionInput body) {
        return ApiResponse.created(disputes.view(service.disputeCash(me, id, body.description())), "Dispute opened");
    }

    @PostMapping("/{id}/charges")
    public ResponseEntity<ApiResponse<ChargeView>> proposeCharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @Valid @RequestBody ChargeInput body) {
        return ApiResponse.created(ChargeView.of(service.proposeCharge(me, id, body)), "Charge proposed");
    }

    @PostMapping("/{id}/charges/{chargeId}/approve")
    public ResponseEntity<ApiResponse<ChargeView>> approveCharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @PathVariable Long chargeId) {
        return ApiResponse.ok(ChargeView.of(service.decideCharge(me, id, chargeId, true)));
    }

    @PostMapping("/{id}/charges/{chargeId}/reject")
    public ResponseEntity<ApiResponse<ChargeView>> rejectCharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                @PathVariable Long chargeId) {
        return ApiResponse.ok(ChargeView.of(service.decideCharge(me, id, chargeId, false)));
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<RatingView>> rate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                        @Valid @RequestBody RatingInput body) {
        Rating r = service.rate(me, id, body);
        return ApiResponse.created(OrderQueryService.ratingView(r, me.fullName()), "Thank you for your rating");
    }
}

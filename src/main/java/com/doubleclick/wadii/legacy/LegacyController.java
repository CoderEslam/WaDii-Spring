package com.doubleclick.wadii.legacy;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.order.OrderService;
import com.doubleclick.wadii.order.dto.OrderDtos.CancelRequest;
import com.doubleclick.wadii.order.dto.OrderDtos.CancelResult;
import com.doubleclick.wadii.order.dto.OrderDtos.CreateOrderRequest;
import com.doubleclick.wadii.order.dto.OrderDtos.OrderView;
import com.doubleclick.wadii.response.ResponseService;
import com.doubleclick.wadii.response.dto.ResponseDtos.AcceptResult;
import com.doubleclick.wadii.response.dto.ResponseDtos.PartPriceRequest;
import com.doubleclick.wadii.response.dto.ResponseDtos.ResponseView;
import com.doubleclick.wadii.response.dto.ResponseDtos.SubmitResponseRequest;
import com.doubleclick.wadii.response.dto.ResponseDtos.UpdateResponseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DEPRECATED compatibility endpoints listed in the spec (section 83) for the current WaDii-KMP client.
 * Each one maps the old request shape onto the new service, so the same business rules apply
 * (identity from JWT, ALL-services matching, offer expiry, atomic accept). Responses use the new DTOs.
 * Every response carries a {@code Deprecation} header and a {@code Link} to the successor endpoint.
 */
@RestController
@RequiredArgsConstructor
public class LegacyController {

    private final OrderService orderService;
    private final ResponseService responseService;

    public record LegacySparePart(String sparePartName) {
    }

    /** Old shape; userId, if sent, is ignored. */
    public record LegacyOrderRequest(String carModelYear, String comment, LocalDate date, BigDecimal latitude, BigDecimal longitude,
                                     String address, List<Long> servicesIds, Long providerId, List<LegacySparePart> spareParts, Long carTypeId) {
    }

    public record LegacyCancelRequest(Long orderId, Long reasonId, String reason) {
    }

    public record LegacyPartPrice(Long id, BigDecimal price) {
    }

    public record LegacyResponseRequest(Long id, Long responseId, Long orderId, String comment, List<LegacyPartPrice> sparePartsPrice,
                                        BigDecimal laborCost, BigDecimal discount, Integer estimatedDurationMinutes, Instant expiresAt) {
    }

    @PostMapping("/orders/insert")
    @PreAuthorize(Permissions.CUSTOMER)
    public ResponseEntity<ApiResponse<OrderView>> insertOrder(@AuthenticationPrincipal AuthUser me, @RequestBody LegacyOrderRequest r) {
        if (r.carModelYear() == null || r.carModelYear().isBlank() || r.date() == null || r.servicesIds() == null || r.servicesIds().isEmpty()) {
            throw BusinessException.validation("carModelYear, date and servicesIds are required");
        }
        List<String> parts = r.spareParts() == null ? null : r.spareParts().stream()
                .map(LegacySparePart::sparePartName).filter(n -> n != null && !n.isBlank()).toList();
        CreateOrderRequest request = new CreateOrderRequest(r.carTypeId(), r.carModelYear(), r.date(), r.address(), r.latitude(),
                r.longitude(), r.comment(), r.servicesIds(), parts, r.providerId());
        return deprecated(ApiResponse.created(orderService.create(me, request), "Order created"), "/api/v1/orders");
    }

    @PostMapping("/orders/cancel")
    public ResponseEntity<ApiResponse<CancelResult>> cancelOrder(@AuthenticationPrincipal AuthUser me, @RequestBody LegacyCancelRequest r) {
        if (r.orderId() == null) throw BusinessException.validation("orderId is required");
        return deprecated(ApiResponse.ok(orderService.cancel(me, r.orderId(), new CancelRequest(r.reasonId(), r.reason())), "Order canceled"),
                "/api/v1/orders/{id}/cancel");
    }

    /** Offers received on the caller's orders (paged; default page size 20, max 100). */
    @GetMapping("/responses/get-all-response-of-user")
    public ResponseEntity<ApiResponse<List<ResponseView>>> responsesOfUser(@AuthenticationPrincipal AuthUser me,
                                                                           @RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        PageResponse<ResponseView> result = responseService.mine(me, page, size);
        return deprecated(ApiResponse.ok(result.items(), "Responses (page " + result.page() + " of " + result.totalPages() + ")"),
                "/api/v1/responses/mine");
    }

    @PostMapping("/responses/insert")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ResponseView>> insertResponse(@AuthenticationPrincipal AuthUser me, @RequestBody LegacyResponseRequest r) {
        if (r.orderId() == null || r.comment() == null || r.comment().isBlank()) {
            throw BusinessException.validation("orderId and comment are required");
        }
        SubmitResponseRequest request = new SubmitResponseRequest(r.orderId(), r.comment(), r.laborCost(), null, r.discount(),
                partPrices(r.sparePartsPrice()), r.estimatedDurationMinutes(), r.expiresAt(), null, null);
        return deprecated(ApiResponse.created(responseService.submit(me, request), "Offer submitted"), "/api/v1/responses");
    }

    @PostMapping("/responses/update")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ResponseView>> updateResponse(@AuthenticationPrincipal AuthUser me, @RequestBody LegacyResponseRequest r) {
        Long id = r.id() != null ? r.id() : r.responseId();
        if (id == null) throw BusinessException.validation("id is required");
        UpdateResponseRequest request = new UpdateResponseRequest(r.comment(), r.laborCost(), null, r.discount(),
                partPrices(r.sparePartsPrice()), r.estimatedDurationMinutes(), r.expiresAt(), null, null);
        return deprecated(ApiResponse.ok(responseService.update(me, id, request), "Offer updated"), "/api/v1/responses/{id}");
    }

    @PostMapping("/responses/accept-response")
    public ResponseEntity<ApiResponse<AcceptResult>> accept(@AuthenticationPrincipal AuthUser me, @RequestBody LegacyResponseRequest r) {
        Long id = r.responseId() != null ? r.responseId() : r.id();
        if (id == null) throw BusinessException.validation("responseId is required");
        return deprecated(ApiResponse.ok(responseService.accept(me, id, r.orderId()), "Offer accepted"), "/api/v1/responses/{id}/accept");
    }

    /** Customer declines a pending offer (kept as DECLINED history). */
    @PostMapping("/responses/cancel-response")
    public ResponseEntity<ApiResponse<ResponseView>> decline(@AuthenticationPrincipal AuthUser me, @RequestBody LegacyResponseRequest r) {
        Long id = r.responseId() != null ? r.responseId() : r.id();
        if (id == null) throw BusinessException.validation("responseId is required");
        return deprecated(ApiResponse.ok(responseService.decline(me, id), "Offer declined"), "/api/v1/responses/{id}/decline");
    }

    private static List<PartPriceRequest> partPrices(List<LegacyPartPrice> prices) {
        return prices == null ? null : prices.stream().map(p -> new PartPriceRequest(p.id(), p.price())).toList();
    }

    private static <T> ResponseEntity<T> deprecated(ResponseEntity<T> response, String successor) {
        return ResponseEntity.status(response.getStatusCode())
                .header("Deprecation", "true")
                .header("Link", "<" + successor + ">; rel=\"successor-version\"")
                .body(response.getBody());
    }
}

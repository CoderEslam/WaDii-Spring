package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.payment.PaymentRepository;
import com.doubleclick.wadii.payment.PaymentService;
import com.doubleclick.wadii.response.OrderResponseRepository;
import com.doubleclick.wadii.response.ResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scheduled jobs. Each item runs in its own transaction (through the service), so one failure never blocks the rest.
 * <ul>
 *   <li>offer expiry: PENDING offers with expires_at &lt;= now become EXPIRED (customer + provider notified)</li>
 *   <li>order deadline: awaiting orders past response_deadline become EXPIRED with their pending offers</li>
 *   <li>rematch: MATCHING orders retry provider matching</li>
 *   <li>auto-complete: IN_PROGRESS orders marked done by the provider, after wadii.order.auto-complete-hours</li>
 *   <li>payment expiry: PENDING payments without proof after wadii.payment.pending-ttl-hours</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderJobs {

    private final OrderRepository orderRepository;
    private final OrderResponseRepository responseRepository;
    private final PaymentRepository paymentRepository;
    private final ResponseService responseService;
    private final OrderExpirationService expirationService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final WadiiProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${wadii.jobs.expiration-interval-ms:60000}", initialDelay = 30000)
    public void runExpirations() {
        expireOffers();
        expireOrders();
        expirePayments();
        autoComplete();
    }

    @Scheduled(fixedDelayString = "${wadii.jobs.rematch-interval-ms:600000}", initialDelay = 60000)
    public void runRematch() {
        each(orderRepository.findIdsAwaitingMatch(clock.instant()), orderService::rematch, "rematch");
    }

    public void expireOffers() {
        List<Long> ids;
        do {
            ids = responseRepository.findIdsOfPendingExpired(clock.instant(), PageRequest.of(0, 200));
            each(ids, responseService::expire, "offer expiry");
        } while (ids.size() == 200);
    }

    public void expireOrders() {
        each(orderRepository.findIdsPastDeadline(OrderStatus.AWAITING_OFFERS, clock.instant()), expirationService::expire, "order expiry");
    }

    public void expirePayments() {
        Instant before = clock.instant().minus(Duration.ofHours(properties.getPayment().getPendingTtlHours()));
        each(paymentRepository.findIdsPendingSince(before), paymentService::expire, "payment expiry");
    }

    public void autoComplete() {
        int hours = properties.getOrder().getAutoCompleteHours();
        if (hours <= 0) return;
        each(orderRepository.findIdsAwaitingAutoComplete(clock.instant().minus(Duration.ofHours(hours))), orderService::autoComplete, "auto-complete");
    }

    private void each(List<Long> ids, Consumer<Long> action, String name) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                log.warn("{} failed for {}: {}", name, id, e.getMessage());
            }
        }
    }
}

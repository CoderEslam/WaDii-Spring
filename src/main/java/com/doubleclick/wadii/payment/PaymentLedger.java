package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.response.OrderResponse;
import com.doubleclick.wadii.response.OrderResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Server-side amounts. The client never chooses what is payable:
 * amountDue = accepted offer price + approved additional charges; outstanding = amountDue - confirmed payments.
 * Refunds are tracked separately and never reduce what had to be paid.
 */
@Component
@RequiredArgsConstructor
public class PaymentLedger {

    private final OrderResponseRepository responseRepository;
    private final AdditionalChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;

    public BigDecimal acceptedPrice(Order order) {
        if (order.getAcceptedResponseId() == null) return BigDecimal.ZERO;
        return responseRepository.findById(order.getAcceptedResponseId()).map(OrderResponse::getPrice).orElse(BigDecimal.ZERO);
    }

    public BigDecimal approvedCharges(Order order) {
        return chargeRepository.sumApproved(order.getId());
    }

    public BigDecimal amountDue(Order order) {
        return acceptedPrice(order).add(approvedCharges(order));
    }

    public BigDecimal paid(Order order) {
        return paymentRepository.sumPaid(order.getId());
    }

    public BigDecimal refunded(Order order) {
        return paymentRepository.sumRefunded(order.getId());
    }

    public BigDecimal outstanding(Order order) {
        return amountDue(order).subtract(paid(order)).max(BigDecimal.ZERO);
    }

    public boolean isFullyPaid(Order order) {
        return amountDue(order).signum() > 0 && outstanding(order).signum() == 0;
    }
}

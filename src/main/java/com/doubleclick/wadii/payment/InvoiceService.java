package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.payment.dto.PaymentDtos.InvoiceView;
import com.doubleclick.wadii.response.OrderResponse;
import com.doubleclick.wadii.response.OrderResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Issues the invoice when an order completes. Every figure is derived from the records:
 * subtotal = labor + parts of the accepted offer, discount from the offer, approved charges, completed refunds,
 * total = subtotal - discount + charges, paid = confirmed payments, remaining = total - paid.
 * platformFee = total x commission%, providerNet = total - refunds - platformFee (no 100% assumption).
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderResponseRepository responseRepository;
    private final PaymentLedger ledger;
    private final OrderAccess orderAccess;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice issue(Order order) {
        return invoiceRepository.findByOrderId(order.getId()).orElseGet(() -> {
            OrderResponse accepted = responseRepository.findById(order.getAcceptedResponseId()).orElseThrow();
            BigDecimal subtotal = accepted.getLaborCost().add(accepted.getPartsCost());
            BigDecimal discount = accepted.getDiscount();
            BigDecimal charges = ledger.approvedCharges(order);
            BigDecimal refunds = ledger.refunded(order);
            BigDecimal total = subtotal.subtract(discount).add(charges);
            BigDecimal paid = ledger.paid(order);
            BigDecimal fee = total.multiply(properties.getCommission().getPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            Invoice i = new Invoice();
            i.setOrderId(order.getId());
            i.setInvoiceNumber("WD-" + LocalDate.now(clock.withZone(properties.zone())).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + order.getId());
            i.setProviderId(order.getAcceptedProvider().getId());
            i.setCustomerId(order.getCustomer().getId());
            i.setSubtotal(subtotal);
            i.setDiscount(discount);
            i.setAdditionalCharges(charges);
            i.setRefunds(refunds);
            i.setTotal(total);
            i.setPaid(paid);
            i.setRemaining(total.subtract(paid).max(BigDecimal.ZERO));
            i.setPlatformFee(fee);
            i.setProviderNet(total.subtract(refunds).subtract(fee));
            i.setCurrency(order.getCurrency());
            i.setIssuedAt(clock.instant());
            i = invoiceRepository.save(i);
            audit.system(AuditAction.INVOICE_ISSUED, "INVOICE", i.getId(), i.getInvoiceNumber());
            timeline.system(order.getId(), TimelineEvent.INVOICE_ISSUED, i.getInvoiceNumber());
            return i;
        });
    }

    @Transactional(readOnly = true)
    public InvoiceView get(AuthUser me, Long orderId) {
        Order order = orderAccess.load(orderId);
        orderAccess.requireParty(me, order);
        return invoiceRepository.findByOrderId(orderId).map(InvoiceView::of)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.INVOICE_NOT_FOUND));
    }
}

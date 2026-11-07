package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.offer.OfferViews.LineInput;
import com.doubleclick.wadii.offer.OfferViews.PartInput;
import com.doubleclick.wadii.settings.Configs.CommissionConfig;
import com.doubleclick.wadii.settings.Configs.CommissionPayer;
import com.doubleclick.wadii.settings.Configs.FeeMode;
import com.doubleclick.wadii.settings.PlatformConfigService.Versioned;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-side pricing of an offer line. The provider sets the prices; this only adds them up and applies the
 * commission in force: base = provider total (never the commission itself), a fixed amount is charged once per
 * Service Order. When the customer pays the commission it is added on top of the provider total; when the provider
 * pays it, the customer total is the provider total.
 */
public final class OfferPricing {

    private OfferPricing() {
    }

    public static void price(OfferLine line, LineInput input, Versioned<CommissionConfig> commission) {
        CommissionConfig cfg = commission.value();
        line.setCommissionMode(cfg.mode());
        line.setCommissionValue(Money.of(cfg.value()));
        line.setCommissionPayer(cfg.payer());
        line.setCommissionConfigId(commission.versionId());
        if (!input.available()) {
            line.setServicePrice(Money.ZERO);
            line.setLaborCost(Money.ZERO);
            line.setPartsTotal(Money.ZERO);
            line.setDiscount(Money.ZERO);
            line.setProviderTotal(Money.ZERO);
            line.setCommissionAmount(Money.ZERO);
            line.setCustomerTotal(Money.ZERO);
            return;
        }
        BigDecimal service = Money.of(input.servicePrice());
        BigDecimal labor = Money.of(input.laborCost());
        BigDecimal parts = partsTotal(input.parts());
        BigDecimal discount = Money.of(input.discount());
        BigDecimal gross = service.add(labor).add(parts);
        if (discount.compareTo(gross) > 0) {
            throw BusinessException.validation("discount cannot exceed the price.");
        }
        BigDecimal providerTotal = gross.subtract(discount);
        if (providerTotal.signum() <= 0) {
            throw BusinessException.validation("An available service needs a price above zero.");
        }
        BigDecimal commissionAmount = commission(cfg.mode(), cfg.value(), providerTotal);
        line.setServicePrice(service);
        line.setLaborCost(labor);
        line.setPartsTotal(parts);
        line.setDiscount(discount);
        line.setProviderTotal(providerTotal);
        line.setCommissionAmount(commissionAmount);
        line.setCustomerTotal(cfg.payer() == CommissionPayer.CUSTOMER ? providerTotal.add(commissionAmount) : providerTotal);
    }

    public static BigDecimal commission(FeeMode mode, BigDecimal value, BigDecimal base) {
        return switch (mode) {
            case NONE -> Money.ZERO;
            case FIXED -> Money.of(value).min(base);
            case PERCENT -> Money.percentOf(base, value);
        };
    }

    public static BigDecimal partTotal(PartInput p) {
        return Money.of(p.unitPrice().multiply(BigDecimal.valueOf(p.quantity())));
    }

    private static BigDecimal partsTotal(List<PartInput> parts) {
        return parts == null ? Money.ZERO : parts.stream().map(OfferPricing::partTotal).reduce(Money.ZERO, BigDecimal::add);
    }
}

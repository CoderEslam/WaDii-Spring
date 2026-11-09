package com.doubleclick.wadii.order;

import com.doubleclick.wadii.offer.OfferViews.Warranty;
import com.doubleclick.wadii.settings.Configs;

import java.math.BigDecimal;
import java.util.List;

/**
 * The terms both parties agreed to, frozen into the order at acceptance. Later edits to the provider's policy or to
 * admin configuration never change it.
 */
public record PolicySnapshot(Integer providerPolicyVersion, String cancellationTerms, String noShowTerms,
                             String serviceConditions, String returnTerms, List<String> platformRules,
                             Warranty warranty, Commission commission) {

    public record Commission(Configs.FeeMode mode, BigDecimal value, Configs.CommissionPayer payer, BigDecimal base,
                             BigDecimal amount) {
    }

    /** Platform rules disclosed with every V1 order. */
    public static List<String> platformRules(int noShowGraceMinutes) {
        return List.of(
                "Payment is made in cash directly to the provider. WaDii does not verify cash payments.",
                "Cancellation before the service starts is free of charge in this version.",
                "Once work has started it cannot be canceled; stopping it follows the interruption and partial-completion process.",
                "A no-show can be reported " + noShowGraceMinutes + " minutes after the appointment time.",
                "The order is completed only when the customer confirms it; there is no automatic completion.",
                "Choosing \"no warranty\" never removes rights granted by Egyptian consumer protection law.");
    }
}

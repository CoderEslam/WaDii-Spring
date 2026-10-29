package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.payment.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Where a provider receives manual transfers (Vodafone Cash wallet number, InstaPay address). */
@Entity
@Table(name = "provider_payment_accounts")
@Getter
@Setter
@NoArgsConstructor
public class ProviderPaymentAccount extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "account_identifier", nullable = false)
    private String accountIdentifier;

    private String instructions;

    @Column(nullable = false)
    private boolean active = true;
}

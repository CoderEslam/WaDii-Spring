package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.payment.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderPaymentAccountRepository extends JpaRepository<ProviderPaymentAccount, Long> {

    List<ProviderPaymentAccount> findByProviderIdOrderById(Long providerId);

    Optional<ProviderPaymentAccount> findByProviderIdAndMethod(Long providerId, PaymentMethod method);

    Optional<ProviderPaymentAccount> findByProviderIdAndMethodAndActiveTrue(Long providerId, PaymentMethod method);
}

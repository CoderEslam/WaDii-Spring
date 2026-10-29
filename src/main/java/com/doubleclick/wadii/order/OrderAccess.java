package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who may see or act on an order, decided from the JWT only:
 * the customer, a provider targeted for it, its accepted provider, or a support admin.
 */
@Component
@RequiredArgsConstructor
public class OrderAccess {

    private final OrderRepository orderRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final ProviderAccess providerAccess;

    public enum Relation { CUSTOMER, ACCEPTED_PROVIDER, TARGETED_PROVIDER, ADMIN }

    public record Access(Order order, Relation relation, Provider provider, OrderProviderTarget target) {
    }

    public Order load(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND));
    }

    public Order loadForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND));
    }

    public Access view(AuthUser me, Order order) {
        if (order.isOwnedBy(me.id())) return new Access(order, Relation.CUSTOMER, null, null);
        Optional<Provider> provider = providerAccess.findOwn(me);
        if (provider.isPresent()) {
            Provider p = provider.get();
            if (order.isAcceptedProvider(p.getId())) {
                return new Access(order, Relation.ACCEPTED_PROVIDER, p, targetRepository.findByOrderIdAndProviderId(order.getId(), p.getId()).orElse(null));
            }
            Optional<OrderProviderTarget> target = targetRepository.findByOrderIdAndProviderId(order.getId(), p.getId());
            if (target.isPresent()) return new Access(order, Relation.TARGETED_PROVIDER, p, target.get());
        }
        if (me.isSupport()) return new Access(order, Relation.ADMIN, null, null);
        throw BusinessException.forbidden("You are not allowed to access this order");
    }

    public Order requireCustomer(AuthUser me, Order order) {
        if (!order.isOwnedBy(me.id())) throw BusinessException.forbidden("Only the customer of this order can do this");
        return order;
    }

    /** The caller must be the accepted provider of the order; returns their provider profile. */
    public Provider requireAcceptedProvider(AuthUser me, Order order) {
        Provider p = providerAccess.requireOwn(me);
        if (!order.isAcceptedProvider(p.getId())) throw BusinessException.forbidden("Only the accepted provider of this order can do this");
        return p;
    }

    /** Customer, accepted provider or support admin (parties of an assigned order). */
    public Relation requireParty(AuthUser me, Order order) {
        if (order.isOwnedBy(me.id())) return Relation.CUSTOMER;
        Optional<Provider> p = providerAccess.findOwn(me);
        if (p.isPresent() && order.isAcceptedProvider(p.get().getId())) return Relation.ACCEPTED_PROVIDER;
        if (me.isSupport() || me.isFinance()) return Relation.ADMIN;
        throw BusinessException.forbidden("You are not a party of this order");
    }
}

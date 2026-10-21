package com.doubleclick.wadii.service;

import com.doubleclick.wadii.entities.Order;
import com.doubleclick.wadii.entities.OrderProviderTarget;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.entities.Service;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.OrderProviderTargetRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Decides which providers receive a new order and notifies them.
 * <ul>
 *   <li>A provider selected by the customer receives the order directly.</li>
 *   <li>Otherwise every active provider that offers ALL the requested services receives it.</li>
 * </ul>
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private final ProviderRepository providerRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final UserNotifier userNotifier;

    /** Validates that the customer-selected provider can receive this order. */
    public Provider validateDirectProvider(Long providerId, Long ownerId, List<Service> services) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ApiException.notFound("there is no provider with this id : " + providerId));
        if (provider.getUser() == null || provider.getUser().getRole() != Role.PROVIDER) {
            throw ApiException.unprocessable("The selected account is not a provider");
        }
        if (provider.getUser().getId().equals(ownerId)) {
            throw ApiException.unprocessable("You cannot send an order to your own provider profile");
        }
        if (!provider.isEligibleForOrders()) {
            throw ApiException.unprocessable("The selected provider is not accepting orders");
        }
        for (Service service : services) {
            boolean offers = service.getProviders() != null
                    && service.getProviders().stream().anyMatch(p -> p.getId().equals(provider.getId()));
            if (!offers) {
                throw ApiException.unprocessable("The selected provider does not offer the service: " + service.getName());
            }
        }
        return provider;
    }

    /** Creates the target rows for an order and notifies the targeted providers. */
    @Transactional
    public List<Provider> targetAndNotify(Order order, Provider directProvider) {
        return target(order, directProvider, true);
    }

    /** Same as targetAndNotify; notify=false is used when backfilling orders that already existed. */
    @Transactional
    public List<Provider> target(Order order, Provider directProvider, boolean notify) {
        List<Provider> providers;
        if (directProvider != null) {
            providers = List.of(directProvider);
        } else {
            List<Long> serviceIds = order.getServices().stream().map(Service::getId).toList();
            providers = providerRepository.findEligibleForAllServices(serviceIds, serviceIds.size(), order.getUser().getId());
        }
        LocalDateTime now = LocalDateTime.now();
        for (Provider provider : providers) {
            if (targetRepository.existsByOrderIdAndProviderId(order.getId(), provider.getId())) continue;
            OrderProviderTarget target = new OrderProviderTarget();
            target.setOrder(order);
            target.setProvider(provider);
            target.setNotifiedAt(now);
            targetRepository.save(target);
            if (!notify) continue;
            // No customer details in the notification, only the order reference.
            userNotifier.notify(provider.getUser(), "New order",
                    "A new order #" + order.getId() + " matches your services.", "NEW_ORDER", order.getId());
        }
        return providers;
    }

    /** Removes the invitations of an order that has not received any response yet. */
    @Transactional
    public void clearTargets(Long orderId) {
        targetRepository.deleteAll(targetRepository.findByOrderId(orderId));
    }
}

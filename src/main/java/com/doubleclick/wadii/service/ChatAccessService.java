package com.doubleclick.wadii.service;

import com.doubleclick.wadii.entities.OrderStatus;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chat, calls and presence are only allowed between a customer and the provider
 * whose response they accepted (the order is ACCEPTED, IN_PROGRESS or COMPLETED).
 */
@Service
@RequiredArgsConstructor
public class ChatAccessService {

    private static final List<OrderStatus> CONNECTED = List.of(
            OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);

    private final OrderRepository orderRepository;

    public boolean canCommunicate(Long userA, Long userB) {
        if (userA == null || userB == null || userA.equals(userB)) return false;
        return orderRepository.existsConnection(userA, userB, CONNECTED);
    }

    public void require(Long userA, Long userB) {
        if (!canCommunicate(userA, userB)) {
            throw ApiException.forbidden("You can only communicate with a user connected to you through an accepted order");
        }
    }

    /** Ids of all users the given user may communicate with. */
    public Set<Long> counterparts(Long userId) {
        Set<Long> ids = new HashSet<>(orderRepository.findAcceptedProviderUserIdsOfCustomer(userId, CONNECTED));
        ids.addAll(orderRepository.findCustomerIdsOfAcceptedProviderUser(userId, CONNECTED));
        ids.remove(userId);
        return ids;
    }
}

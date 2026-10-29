package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.provider.Provider;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.doubleclick.wadii.order.OrderStatus.*;

/** The formal order state machine. Every status change goes through {@link #transition}. */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(CREATED, EnumSet.of(MATCHING, QUOTING, CANCELED));
        ALLOWED.put(MATCHING, EnumSet.of(QUOTING, CANCELED, EXPIRED));
        ALLOWED.put(QUOTING, EnumSet.of(RESPONSES_AVAILABLE, CANCELED, EXPIRED));
        ALLOWED.put(RESPONSES_AVAILABLE, EnumSet.of(QUOTING, ACCEPTED, CANCELED, EXPIRED));
        ALLOWED.put(ACCEPTED, EnumSet.of(IN_PROGRESS, CANCELED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED, CANCELED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(EXPIRED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void transition(Order order, OrderStatus next, Instant at) {
        if (!canTransition(order.getStatus(), next)) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE,
                    "The order cannot move from " + order.getStatus() + " to " + next);
        }
        order.transitionTo(next);
        switch (next) {
            case IN_PROGRESS -> order.setStartedAt(at);
            case COMPLETED -> order.setCompletedAt(at);
            case CANCELED -> order.setCanceledAt(at);
            case EXPIRED -> order.setExpiredAt(at);
            default -> {
            }
        }
    }

    public static void accept(Order order, Provider provider, Long responseId, Instant at) {
        transition(order, ACCEPTED, at);
        order.assignAcceptedResponse(provider, responseId, at);
    }
}

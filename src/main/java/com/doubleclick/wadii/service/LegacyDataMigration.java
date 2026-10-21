package com.doubleclick.wadii.service;

import com.doubleclick.wadii.entities.OrderStatus;
import com.doubleclick.wadii.entities.Responses;
import com.doubleclick.wadii.entities.ResponsesState;
import com.doubleclick.wadii.repository.OrderProviderTargetRepository;
import com.doubleclick.wadii.repository.OrderRepository;
import com.doubleclick.wadii.repository.ResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * One-off, idempotent upgrade of rows written before the order/response flow changed:
 * renames the old response states, records the winning provider on already accepted orders and
 * invites providers to orders that are still open (without sending notifications).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(10)
public class LegacyDataMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OrderRepository orderRepository;
    private final ResponseRepository responseRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final OrderMatchingService orderMatchingService;

    @Override
    public void run(ApplicationArguments args) {
        renameResponseStates();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                backfillAcceptedOrders();
                backfillOpenOrderTargets();
            });
        } catch (Exception e) {
            log.warn("Legacy order backfill skipped: {}", e.getMessage());
        }
    }

    private void renameResponseStates() {
        try {
            int accepted = jdbcTemplate.update("UPDATE responses SET responses_state = 'ACCEPTED' WHERE responses_state = 'ACCEPT'");
            int canceled = jdbcTemplate.update("UPDATE responses SET responses_state = 'CANCELED' WHERE responses_state = 'CANCEL'");
            if (accepted + canceled > 0) {
                log.info("Renamed legacy response states: {} ACCEPT, {} CANCEL", accepted, canceled);
            }
        } catch (Exception e) {
            log.warn("Response state rename skipped: {}", e.getMessage());
        }
    }

    private void backfillAcceptedOrders() {
        for (Responses response : responseRepository.findAll()) {
            if (response.getResponsesState() != ResponsesState.ACCEPTED) continue;
            com.doubleclick.wadii.entities.Order order = response.getOrder();
            if (order.getAcceptedProvider() == null) {
                order.setAcceptedProvider(response.getProvider());
                order.setAcceptedResponseId(response.getId());
                if (order.getStatus() == null || order.getStatus() == OrderStatus.PENDING) {
                    order.setStatus(OrderStatus.ACCEPTED);
                }
                orderRepository.save(order);
            }
        }
    }

    private void backfillOpenOrderTargets() {
        List<com.doubleclick.wadii.entities.Order> orders = orderRepository.findAll();
        for (com.doubleclick.wadii.entities.Order order : orders) {
            boolean open = order.getStatus() == null || order.getStatus().isOpen();
            if (!open || order.getServices() == null || order.getServices().isEmpty()) continue;
            if (!targetRepository.findByOrderId(order.getId()).isEmpty()) continue;
            orderMatchingService.target(order, order.getProvider(), false);
        }
    }
}

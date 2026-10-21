package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.OrderCancelRequest;
import com.doubleclick.wadii.dto.OrderDto;
import com.doubleclick.wadii.dto.SparePartsDto;
import com.doubleclick.wadii.entities.*;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.*;
import com.doubleclick.wadii.service.OrderMatchingService;
import com.doubleclick.wadii.service.UserNotifier;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/orders")
public class OrderController extends Controller<Order, OrderDto, Long> {

    private final OrderRepository orderRepository;
    private final SparePartsRepository sparePartsRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;
    private final OrderCancelRepository orderCancelRepository;
    private final ReasonRepository reasonRepository;
    private final ResponseRepository responseRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final CurrentUserService currentUserService;
    private final OrderMatchingService orderMatchingService;
    private final UserNotifier userNotifier;

    // ---------------------------------------------------------------- read

    /** Visible to the owner, a targeted provider, the accepted provider and admins. */
    @Override
    public ResponseEntity<Response<Order>> show(Long id) {
        User caller = currentUserService.user();
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + id));
        applyVisibility(order, caller, true);
        return Response.response(order, "Done", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Order>>> readAll() {
        currentUserService.admin();
        return Response.response(orderRepository.findAll(), "All orders", ResponseType.SUCCESS);
    }

    @GetMapping("/show-all-order-of-user")
    public ResponseEntity<Response<List<Order>>> getOrdersByUserId(Authentication authentication) {
        User user = currentUserService.user(authentication);
        List<Order> orders = orderRepository.findOrderByUserId(user.getId()).orElse(Collections.emptyList());
        return Response.response(orders, orders.isEmpty() ? "There is no orders there" : "All orders", ResponseType.SUCCESS);
    }

    /** Orders the provider was invited to (selected directly or matched by services). */
    @GetMapping("/show-all-order-of-provider")
    public ResponseEntity<Response<List<Order>>> getOrdersByProviderToken(Authentication authentication) {
        User user = currentUserService.user(authentication);
        Provider provider = currentUserService.providerOf(user);
        List<Order> orders = targetRepository.findOrdersByProviderId(provider.getId());
        for (Order order : orders) {
            showOnlyOwnResponses(order, provider);
        }
        return Response.response(orders, "All orders for provider", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- create / update / delete

    @Override
    @Transactional
    public ResponseEntity<Response<Order>> insert(Authentication authentication, @RequestBody OrderDto dto) {
        // The owner always comes from the JWT; dto.userId is ignored.
        User user = currentUserService.user(authentication);
        validateOrderFields(dto, true);
        List<Service> services = loadServices(dto.getServicesIds());
        Provider direct = dto.getProviderId() == null ? null
                : orderMatchingService.validateDirectProvider(dto.getProviderId(), user.getId(), services);

        Order order = new Order();
        order.setDate(dto.getDate());
        order.setUser(user);
        order.setComment(dto.getComment());
        order.setCarModelYear(dto.getCarModelYear());
        order.setLatitude(dto.getLatitude());
        order.setLongitude(dto.getLongitude());
        order.setServices(services);
        order.setProvider(direct);
        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);

        order.setSpareParts(saveSpareParts(order, dto.getSpareParts()));
        linkServices(order, services);
        orderMatchingService.targetAndNotify(order, direct);
        return Response.response(order, "Order saved successfully", ResponseType.SUCCESS);
    }

    /** Owner only, and only while no provider has responded yet. */
    @Override
    @Transactional
    public ResponseEntity<Response<Order>> update(Authentication authentication, OrderDto dto) {
        User user = currentUserService.user(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("order id is required");
        }
        Order order = orderRepository.findByIdForUpdate(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + dto.getId()));
        requireOwner(order, user);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("Only orders without responses can be edited; cancel the order and create a new one");
        }
        validateOrderFields(dto, false);

        if (dto.getCarModelYear() != null) order.setCarModelYear(dto.getCarModelYear());
        if (dto.getComment() != null) order.setComment(dto.getComment());
        if (dto.getDate() != null) order.setDate(dto.getDate());
        if (dto.getLatitude() != null) order.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) order.setLongitude(dto.getLongitude());

        if (dto.getServicesIds() != null && !dto.getServicesIds().isEmpty()) {
            List<Service> newServices = loadServices(dto.getServicesIds());
            if (order.getProvider() != null) {
                orderMatchingService.validateDirectProvider(order.getProvider().getId(), user.getId(), newServices);
            }
            if (order.getServices() != null) {
                for (Service service : order.getServices()) {
                    if (service.getOrders() != null) {
                        service.getOrders().remove(order);
                        serviceRepository.save(service);
                    }
                }
            }
            order.setServices(newServices);
            linkServices(order, newServices);
            // The eligible providers depend on the services, so invite the right ones again.
            orderMatchingService.clearTargets(order.getId());
            orderRepository.save(order);
            orderMatchingService.targetAndNotify(order, order.getProvider());
        }
        order = orderRepository.save(order);

        if (dto.getSpareParts() != null && !dto.getSpareParts().isEmpty()) {
            if (order.getSpareParts() != null && !order.getSpareParts().isEmpty()) {
                sparePartsRepository.deleteAll(order.getSpareParts());
            }
            order.setSpareParts(saveSpareParts(order, dto.getSpareParts()));
        }
        return Response.response(order, "Order updated successfully", ResponseType.SUCCESS);
    }

    /** Owner (or admin) can delete an order that is still PENDING or already CANCELED. */
    @Override
    @Transactional
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        User user = currentUserService.user(authentication);
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + id));
        if (!currentUserService.isAdmin(user)) {
            requireOwner(order, user);
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CANCELED) {
            throw ApiException.conflict("Only pending or canceled orders can be deleted");
        }
        if (responseRepository.findByOrderId(order.getId()).stream().anyMatch(r -> r.getResponsesState() == ResponsesState.ACCEPTED)) {
            throw ApiException.conflict("An order with an accepted response cannot be deleted");
        }
        orderMatchingService.clearTargets(order.getId());
        orderCancelRepository.deleteByOrderId(order.getId());
        if (order.getServices() != null) {
            for (Service service : order.getServices()) {
                if (service.getOrders() != null) {
                    service.getOrders().remove(order);
                    serviceRepository.save(service);
                }
            }
        }
        orderRepository.delete(order);
        return Response.response(true, "order deleted successfully", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- lifecycle

    @PostMapping("/cancel")
    @Transactional
    public ResponseEntity<Response<Order>> cancelOrder(Authentication authentication, @RequestBody OrderCancelRequest request) {
        User user = currentUserService.user(authentication);
        if (request.getOrderId() == null) {
            throw ApiException.badRequest("order id is required");
        }
        if (request.getReasonId() == null) {
            throw ApiException.badRequest("reason id is required");
        }
        Order order = orderRepository.findByIdForUpdate(request.getOrderId().longValue())
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + request.getOrderId()));
        requireOwner(order, user);
        Reason reason = reasonRepository.findById(request.getReasonId())
                .orElseThrow(() -> ApiException.notFound("there is no reason with this id : " + request.getReasonId()));
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELED) {
            throw ApiException.conflict("The order is already canceled");
        }
        if (status == OrderStatus.IN_PROGRESS || status == OrderStatus.COMPLETED) {
            throw ApiException.conflict("An order that is " + status + " cannot be canceled");
        }

        order.setStatus(OrderStatus.CANCELED);
        order = orderRepository.save(order);
        OrderCancel orderCancel = new OrderCancel();
        orderCancel.setOrder(order);
        orderCancel.setReason(reason);
        orderCancelRepository.save(orderCancel);

        // Responses are kept as history; open or winning ones become CANCELED and the providers are told.
        for (Responses response : responseRepository.findByOrderId(order.getId())) {
            if (response.getResponsesState() == ResponsesState.PENDING || response.getResponsesState() == ResponsesState.ACCEPTED) {
                response.setResponsesState(ResponsesState.CANCELED);
                responseRepository.save(response);
                userNotifier.notify(response.getProvider().getUser(), "Order canceled",
                        "Order #" + order.getId() + " was canceled by the customer.", "ORDER_CANCELED", order.getId());
            }
        }
        return Response.response(order, "Order canceled successfully", ResponseType.SUCCESS);
    }

    /** The accepted provider starts the job: ACCEPTED -> IN_PROGRESS. */
    @PostMapping("/start/{id}")
    @Transactional
    public ResponseEntity<Response<Order>> start(Authentication authentication, @PathVariable Long id) {
        Provider provider = currentUserService.provider(authentication);
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + id));
        if (order.getAcceptedProvider() == null || !order.getAcceptedProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("Only the accepted provider can start this order");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw ApiException.conflict("Only an ACCEPTED order can be started");
        }
        order.setStatus(OrderStatus.IN_PROGRESS);
        order = orderRepository.save(order);
        userNotifier.notify(order.getUser(), "Service started",
                "The provider started working on order #" + order.getId() + ".", "ORDER_STARTED", order.getId());
        return Response.response(order, "Order started", ResponseType.SUCCESS);
    }

    /** The customer confirms the service is done: ACCEPTED / IN_PROGRESS -> COMPLETED. Enables rating. */
    @PostMapping("/complete/{id}")
    @Transactional
    public ResponseEntity<Response<Order>> complete(Authentication authentication, @PathVariable Long id) {
        User user = currentUserService.user(authentication);
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + id));
        requireOwner(order, user);
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw ApiException.conflict("Only an ACCEPTED or IN_PROGRESS order can be completed");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order = orderRepository.save(order);
        if (order.getAcceptedProvider() != null) {
            userNotifier.notify(order.getAcceptedProvider().getUser(), "Order completed",
                    "Order #" + order.getId() + " was marked as completed.", "ORDER_COMPLETED", order.getId());
        }
        return Response.response(order, "Order completed", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- helpers

    private void requireOwner(Order order, User user) {
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only manage orders you created");
        }
    }

    /**
     * Applies the viewer's access rules. Owners and admins see everything; a provider only sees
     * orders they were invited to and only their own response.
     */
    private Provider applyVisibility(Order order, User caller, boolean markViewed) {
        if (currentUserService.isAdmin(caller) || (order.getUser() != null && order.getUser().getId().equals(caller.getId()))) {
            return null;
        }
        Provider provider = providerRepository.findByUserId(caller.getId()).orElse(null);
        if (provider != null) {
            Optional<OrderProviderTarget> target = targetRepository.findByOrderIdAndProviderId(order.getId(), provider.getId());
            boolean accepted = order.getAcceptedProvider() != null && order.getAcceptedProvider().getId().equals(provider.getId());
            if (target.isPresent() || accepted) {
                if (markViewed && target.isPresent() && target.get().getStatus() == TargetStatus.TARGETED) {
                    target.get().setStatus(TargetStatus.VIEWED);
                    target.get().setViewedAt(LocalDateTime.now());
                    targetRepository.save(target.get());
                }
                showOnlyOwnResponses(order, provider);
                return provider;
            }
        }
        throw ApiException.forbidden("You are not allowed to access this order");
    }

    private void showOnlyOwnResponses(Order order, Provider provider) {
        List<Responses> all = order.getResponses() == null ? Collections.emptyList() : order.getResponses();
        order.setResponsesView(all.stream()
                .filter(r -> r.getProvider() != null && r.getProvider().getId().equals(provider.getId()))
                .toList());
    }

    private void validateOrderFields(OrderDto dto, boolean creating) {
        if (creating) {
            if (dto.getCarModelYear() == null || dto.getCarModelYear().trim().isEmpty()) {
                throw ApiException.badRequest("carModelYear is required");
            }
            if (dto.getDate() == null) {
                throw ApiException.badRequest("date is required");
            }
            if (dto.getServicesIds() == null || dto.getServicesIds().isEmpty()) {
                throw ApiException.badRequest("servicesIds must not be empty");
            }
        }
        if (dto.getCarModelYear() != null && dto.getCarModelYear().trim().isEmpty()) {
            throw ApiException.badRequest("carModelYear must not be blank");
        }
        // One day of tolerance for clients in a different timezone than the server.
        if (dto.getDate() != null && dto.getDate().isBefore(LocalDate.now().minusDays(1))) {
            throw ApiException.unprocessable("date cannot be in the past");
        }
        if (dto.getLatitude() != null && (dto.getLatitude() < -90 || dto.getLatitude() > 90)) {
            throw ApiException.badRequest("latitude must be between -90 and 90");
        }
        if (dto.getLongitude() != null && (dto.getLongitude() < -180 || dto.getLongitude() > 180)) {
            throw ApiException.badRequest("longitude must be between -180 and 180");
        }
        if (dto.getSpareParts() != null) {
            for (SparePartsDto part : dto.getSpareParts()) {
                if (part.getSparePartName() == null || part.getSparePartName().trim().isEmpty()) {
                    throw ApiException.badRequest("sparePartName must not be blank");
                }
            }
        }
    }

    private List<Service> loadServices(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<Service> services = serviceRepository.findAllById(distinct);
        if (services.size() != distinct.size()) {
            throw ApiException.badRequest("One or more service ids do not exist");
        }
        return services;
    }

    private List<SpareParts> saveSpareParts(Order order, List<SparePartsDto> dtos) {
        List<SpareParts> saved = new ArrayList<>();
        if (dtos == null) return saved;
        for (SparePartsDto dto : dtos) {
            SpareParts part = new SpareParts();
            part.setSparePartName(dto.getSparePartName().trim());
            part.setOrder(order);
            saved.add(sparePartsRepository.save(part));
        }
        return saved;
    }

    /** Service owns the services_orders join table, so the order must be added on that side. */
    private void linkServices(Order order, List<Service> services) {
        for (Service service : services) {
            if (service.getOrders() != null) {
                service.getOrders().add(order);
            } else {
                List<Order> orders = new ArrayList<>();
                orders.add(order);
                service.setOrders(orders);
            }
            serviceRepository.save(service);
        }
    }
}

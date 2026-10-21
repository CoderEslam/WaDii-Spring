package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.ResponseDto;
import com.doubleclick.wadii.dto.SparePartsPriceDto;
import com.doubleclick.wadii.entities.*;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.OrderProviderTargetRepository;
import com.doubleclick.wadii.repository.OrderRepository;
import com.doubleclick.wadii.repository.ResponseRepository;
import com.doubleclick.wadii.service.UserNotifier;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/responses")
public class ResponseController extends Controller<Responses, ResponseDto, Long> {

    private final ResponseRepository responseRepository;
    private final OrderRepository orderRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final CurrentUserService currentUserService;
    private final UserNotifier userNotifier;

    // ---------------------------------------------------------------- read

    /** Visible to admins, the order owner and the provider who wrote it. */
    @Override
    public ResponseEntity<Response<Responses>> show(Long id) {
        User caller = currentUserService.user();
        Responses response = responseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("there is no response with this id : " + id));
        boolean admin = currentUserService.isAdmin(caller);
        boolean owner = response.getOrder().getUser().getId().equals(caller.getId());
        boolean author = response.getProvider().getUser().getId().equals(caller.getId());
        if (!admin && !owner && !author) {
            throw ApiException.forbidden("You are not allowed to access this response");
        }
        return Response.response(response, "Done", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Responses>>> readAll() {
        currentUserService.admin();
        return Response.response(responseRepository.findAll(), "All responses", ResponseType.SUCCESS);
    }

    /** Responses (in every state) to the orders of the current customer. */
    @GetMapping("/get-all-response-of-user")
    public ResponseEntity<Response<List<Responses>>> findAllByUserId(Authentication authentication) {
        User user = currentUserService.user(authentication);
        return Response.response(responseRepository.findAllByUserId(user.getId()), "All responses", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- provider side

    /** The provider always comes from the JWT; providerId in the body is ignored. */
    @Override
    @Transactional
    public ResponseEntity<Response<Responses>> insert(Authentication authentication, @RequestBody ResponseDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getOrderId() == null) {
            throw ApiException.badRequest("orderId is required");
        }
        if (dto.getComment() == null || dto.getComment().trim().isEmpty()) {
            throw ApiException.badRequest("comment is required");
        }
        // Lock the order so a response cannot slip in while the customer is accepting another one.
        Order order = orderRepository.findByIdForUpdate(dto.getOrderId())
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + dto.getOrderId()));

        if (!provider.isEligibleForOrders()) {
            throw ApiException.forbidden("Your provider account is not active or not accepting orders");
        }
        OrderProviderTarget target = targetRepository.findByOrderIdAndProviderId(order.getId(), provider.getId())
                .orElseThrow(() -> ApiException.forbidden("You were not invited to respond to this order"));
        if (!order.getStatus().isOpen()) {
            throw ApiException.conflict("This order is no longer open for responses");
        }
        boolean alreadyResponded = responseRepository.findByOrderId(order.getId()).stream()
                .anyMatch(r -> r.getProvider().getId().equals(provider.getId()));
        if (alreadyResponded) {
            throw ApiException.conflict("You already responded to this order");
        }

        Responses response = new Responses();
        response.setComment(dto.getComment().trim());
        response.setLatitude(dto.getLatitude());
        response.setLongitude(dto.getLongitude());
        response.setProvider(provider);
        response.setOrder(order);
        response.setResponsesState(ResponsesState.PENDING);
        applyPrices(response, order, dto.getSparePartsPrice());
        response = responseRepository.save(response);

        target.setStatus(TargetStatus.RESPONDED);
        target.setRespondedAt(LocalDateTime.now());
        targetRepository.save(target);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.RESPONSES_AVAILABLE);
            orderRepository.save(order);
        }
        userNotifier.notify(order.getUser(), "New offer",
                "You received a new offer for order #" + order.getId() + ".", "NEW_RESPONSE", order.getId());
        return Response.response(response, "Response saved successfully", ResponseType.SUCCESS);
    }

    @Override
    @Transactional
    public ResponseEntity<Response<Responses>> update(Authentication authentication, ResponseDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("response id is required");
        }
        Responses response = responseRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no response with this id : " + dto.getId()));
        if (!response.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only update responses you created");
        }
        Order order = orderRepository.findByIdForUpdate(response.getOrder().getId())
                .orElseThrow(() -> ApiException.notFound("order not found"));
        if (response.getResponsesState() != ResponsesState.PENDING || !order.getStatus().isOpen()) {
            throw ApiException.conflict("Only a pending response on an open order can be updated");
        }
        if (dto.getComment() != null) {
            if (dto.getComment().trim().isEmpty()) {
                throw ApiException.badRequest("comment must not be blank");
            }
            response.setComment(dto.getComment().trim());
        }
        if (dto.getLatitude() != null) response.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) response.setLongitude(dto.getLongitude());
        if (dto.getSparePartsPrice() != null && !dto.getSparePartsPrice().isEmpty()) {
            applyPrices(response, order, dto.getSparePartsPrice());
        }
        response = responseRepository.save(response);
        return Response.response(response, "Response updated successfully", ResponseType.SUCCESS);
    }

    /** A provider withdraws their pending response. It is kept as history with state CANCELED. */
    @Override
    @Transactional
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Provider provider = currentUserService.provider(authentication);
        Responses response = responseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("there is no response with this id : " + id));
        if (!response.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only cancel responses you created");
        }
        orderRepository.findByIdForUpdate(response.getOrder().getId());
        if (response.getResponsesState() != ResponsesState.PENDING) {
            throw ApiException.conflict("Only a pending response can be canceled");
        }
        response.setResponsesState(ResponsesState.CANCELED);
        responseRepository.save(response);
        return Response.response(true, "response canceled successfully", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- customer side

    /**
     * Accepts exactly one response: the chosen one becomes ACCEPTED, every other pending
     * response is DECLINED (never deleted), and the order records the winning provider.
     * Runs under a row lock on the order so two requests can never both win.
     */
    @PostMapping("/accept-response")
    @Transactional
    public ResponseEntity<Response<Responses>> accept(Authentication authentication, @RequestBody ResponseDto dto) {
        User user = currentUserService.user(authentication);
        Long responseId = requestedResponseId(dto);
        Long orderId = responseRepository.findById(responseId)
                .orElseThrow(() -> ApiException.notFound("there is no response with this id : " + responseId))
                .getOrder().getId();

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + orderId));
        requireOrderOwner(order, user);
        if (!order.getStatus().isOpen()) {
            throw ApiException.conflict("This order is not open: it is " + order.getStatus());
        }

        List<Responses> all = responseRepository.findByOrderId(order.getId());
        Responses chosen = all.stream().filter(r -> r.getId().equals(responseId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("The response does not belong to this order"));
        if (all.stream().anyMatch(r -> r.getResponsesState() == ResponsesState.ACCEPTED)) {
            throw ApiException.conflict("A response was already accepted for this order");
        }
        if (chosen.getResponsesState() != ResponsesState.PENDING) {
            throw ApiException.conflict("Only a pending response can be accepted");
        }

        chosen.setResponsesState(ResponsesState.ACCEPTED);
        responseRepository.save(chosen);
        userNotifier.notify(chosen.getProvider().getUser(), "Offer accepted",
                "Your offer for order #" + order.getId() + " was accepted.", "RESPONSE_ACCEPTED", order.getId());

        for (Responses other : all) {
            if (!other.getId().equals(chosen.getId()) && other.getResponsesState() == ResponsesState.PENDING) {
                other.setResponsesState(ResponsesState.DECLINED);
                responseRepository.save(other);
                userNotifier.notify(other.getProvider().getUser(), "Offer not selected",
                        "Your offer for order #" + order.getId() + " was not selected.", "RESPONSE_DECLINED", order.getId());
            }
        }

        order.setStatus(OrderStatus.ACCEPTED);
        order.setAcceptedProvider(chosen.getProvider());
        order.setAcceptedResponseId(chosen.getId());
        orderRepository.save(order);
        return Response.response(chosen, "Response accepted", ResponseType.SUCCESS);
    }

    /** The customer declines one pending response. It stays as history with state DECLINED. */
    @PostMapping("/cancel-response")
    @Transactional
    public ResponseEntity<Response<Responses>> cancel(Authentication authentication, @RequestBody ResponseDto dto) {
        User user = currentUserService.user(authentication);
        Long responseId = requestedResponseId(dto);
        Responses response = responseRepository.findById(responseId)
                .orElseThrow(() -> ApiException.notFound("there is no response with this id : " + responseId));
        Order order = orderRepository.findByIdForUpdate(response.getOrder().getId())
                .orElseThrow(() -> ApiException.notFound("order not found"));
        requireOrderOwner(order, user);
        if (response.getResponsesState() != ResponsesState.PENDING || !order.getStatus().isOpen()) {
            throw ApiException.conflict("Only a pending response on an open order can be declined");
        }
        response.setResponsesState(ResponsesState.DECLINED);
        responseRepository.save(response);
        userNotifier.notify(response.getProvider().getUser(), "Offer not selected",
                "Your offer for order #" + order.getId() + " was declined.", "RESPONSE_REJECTED", order.getId());
        return Response.response(response, "Response rejected", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- helpers

    private Long requestedResponseId(ResponseDto dto) {
        Long id = dto.getResponseId() != null ? dto.getResponseId() : dto.getId();
        if (id == null) {
            throw ApiException.badRequest("responseId is required");
        }
        return id;
    }

    private void requireOrderOwner(Order order, User user) {
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only manage responses to your own orders");
        }
    }

    /**
     * Replaces the response's spare-part prices after checking that every price refers to a spare part
     * of this order and is not negative, and recomputes the total on the server.
     */
    private void applyPrices(Responses response, Order order, List<SparePartsPriceDto> dtos) {
        if (response.getSparePartsPrices() == null) {
            response.setSparePartsPrices(new ArrayList<>());
        }
        response.getSparePartsPrices().clear();
        BigDecimal total = BigDecimal.ZERO;
        if (dtos != null) {
            Map<Long, SpareParts> orderParts = new HashMap<>();
            if (order.getSpareParts() != null) {
                for (SpareParts part : order.getSpareParts()) {
                    orderParts.put(part.getId(), part);
                }
            }
            Set<Long> seen = new HashSet<>();
            for (SparePartsPriceDto dto : dtos) {
                if (dto.getId() == null || !orderParts.containsKey(dto.getId())) {
                    throw ApiException.badRequest("Spare part " + dto.getId() + " does not belong to this order");
                }
                if (!seen.add(dto.getId())) {
                    throw ApiException.badRequest("Spare part " + dto.getId() + " is priced more than once");
                }
                if (dto.getPrice() == null || dto.getPrice().signum() < 0) {
                    throw ApiException.badRequest("price must be zero or greater");
                }
                SparePartsPrice price = new SparePartsPrice();
                price.setResponse(response);
                price.setSparePart(orderParts.get(dto.getId()));
                price.setPrice(dto.getPrice());
                response.getSparePartsPrices().add(price);
                total = total.add(dto.getPrice());
            }
        }
        response.setTotalPrice(total);
    }
}

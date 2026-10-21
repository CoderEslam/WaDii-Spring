package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.RateDto;
import com.doubleclick.wadii.entities.Order;
import com.doubleclick.wadii.entities.OrderStatus;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Rate;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.OrderRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.RateRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/rate")
public class RateController extends Controller<Rate, RateDto, Long> {

    private final RateRepository rateRepository;
    private final ProviderRepository providerRepository;
    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;

    @Override
    public ResponseEntity<Response<Rate>> show(Long id) {
        return rateRepository.findById(id)
                .map(rate -> Response.response(rate, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no rate with this id : " + id, ResponseType.NOT_FOUND));
    }

    /**
     * A customer can rate the provider of one of their COMPLETED orders, once per order.
     * The rating user comes from the JWT and the provider from the order.
     */
    @Override
    @Transactional
    public ResponseEntity<Response<Rate>> insert(Authentication authentication, @RequestBody RateDto dto) {
        User user = currentUserService.user(authentication);
        validateScore(dto);
        if (dto.getOrderId() == null) {
            throw ApiException.badRequest("orderId is required");
        }
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> ApiException.notFound("there is no order with this id : " + dto.getOrderId()));
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only rate providers of your own orders");
        }
        if (order.getStatus() != OrderStatus.COMPLETED || order.getAcceptedProvider() == null) {
            throw ApiException.conflict("You can rate a provider only after the order is completed");
        }
        Provider provider = order.getAcceptedProvider();
        if (dto.getProviderId() != null && !dto.getProviderId().equals(provider.getId())) {
            throw ApiException.badRequest("providerId does not match the provider of this order");
        }
        if (rateRepository.existsByUserIdAndOrderId(user.getId(), order.getId())) {
            throw ApiException.conflict("You already rated this order");
        }

        Rate rate = new Rate();
        rate.setRate(dto.getRate());
        rate.setComment(dto.getComment());
        rate.setProvider(provider);
        rate.setUser(user);
        rate.setOrder(order);
        rate = rateRepository.save(rate);
        refreshProviderAverage(provider);
        return Response.response(rate, "Rate saved successfully", ResponseType.SUCCESS);
    }

    /** The author can change the score and comment of their own rating. */
    @Override
    @Transactional
    public ResponseEntity<Response<Rate>> update(Authentication authentication, RateDto dto) {
        User user = currentUserService.user(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("rate id is required");
        }
        validateScore(dto);
        Rate rate = rateRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("Rate with this id = " + dto.getId() + " not exist"));
        if (!rate.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only update rates you created");
        }
        rate.setRate(dto.getRate());
        rate.setComment(dto.getComment());
        rate = rateRepository.save(rate);
        refreshProviderAverage(rate.getProvider());
        return Response.response(rate, "Rate saved successfully", ResponseType.SUCCESS);
    }

    @Override
    @Transactional
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        User user = currentUserService.user(authentication);
        Rate rate = rateRepository.findById(id).orElse(null);
        if (rate == null) {
            return Response.response(false, "there is no rate with this id : " + id, ResponseType.NOT_FOUND);
        }
        if (!currentUserService.isAdmin(user) && !rate.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only delete rates you created");
        }
        Provider provider = rate.getProvider();
        rateRepository.delete(rate);
        rateRepository.flush();
        refreshProviderAverage(provider);
        return Response.response(true, "rate deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Rate>>> readAll() {
        return Response.response(rateRepository.findAll(), "All rates", ResponseType.SUCCESS);
    }

    private void validateScore(RateDto dto) {
        if (dto.getRate() < 1.0 || dto.getRate() > 5.0) {
            throw ApiException.badRequest("rate must be between 1 and 5");
        }
        if (dto.getComment() != null && dto.getComment().length() > 2000) {
            throw ApiException.badRequest("comment is too long");
        }
    }

    private void refreshProviderAverage(Provider provider) {
        Double avg = rateRepository.findAverageRateByProviderId(provider.getId());
        provider.setRate(avg != null ? avg : 0.0);
        providerRepository.save(provider);
    }
}

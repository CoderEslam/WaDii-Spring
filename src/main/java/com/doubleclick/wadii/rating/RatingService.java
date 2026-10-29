package com.doubleclick.wadii.rating;

import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Ratings: only the customer of a COMPLETED order, once per order (UNIQUE(order_id, customer_id)). */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ProviderRepository providerRepository;
    private final OrderAccess orderAccess;
    private final OrderTimelineService timeline;

    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer score, @Size(max = 2000) String comment) {
    }

    public record RatingView(Long id, Long orderId, Long providerId, Long customerId, int score, String comment,
                             Instant createdAt, Instant updatedAt) {
        static RatingView of(Rating r) {
            return new RatingView(r.getId(), r.getOrderId(), r.getProviderId(), r.getCustomerId(), r.getScore(), r.getComment(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    @Transactional
    public RatingView rate(AuthUser me, Long orderId, RatingRequest r) {
        Order order = orderAccess.loadForUpdate(orderId);
        orderAccess.requireCustomer(me, order);
        if (order.getStatus() != OrderStatus.COMPLETED || order.getAcceptedProvider() == null) {
            throw BusinessException.of(ErrorCode.RATING_NOT_ALLOWED);
        }
        if (ratingRepository.existsByOrderIdAndCustomerId(orderId, me.id())) throw BusinessException.of(ErrorCode.RATING_ALREADY_EXISTS);
        Rating rating = new Rating();
        rating.setOrderId(orderId);
        rating.setCustomerId(me.id());
        rating.setProviderId(order.getAcceptedProvider().getId());
        rating.setScore(r.score());
        rating.setComment(r.comment());
        rating = ratingRepository.save(rating);
        ratingRepository.flush();
        refresh(rating.getProviderId());
        timeline.add(orderId, TimelineEvent.RATED, me.id(), ActorRole.CUSTOMER, r.score() + "/5");
        return RatingView.of(rating);
    }

    @Transactional
    public RatingView update(AuthUser me, Long ratingId, RatingRequest r) {
        Rating rating = load(ratingId);
        if (!rating.getCustomerId().equals(me.id())) throw BusinessException.forbidden("You can only edit your own rating");
        rating.setScore(r.score());
        rating.setComment(r.comment());
        ratingRepository.flush();
        refresh(rating.getProviderId());
        return RatingView.of(rating);
    }

    /** The author, or a content/support admin (moderation). */
    @Transactional
    public void delete(AuthUser me, Long ratingId) {
        Rating rating = load(ratingId);
        boolean moderator = me.hasAdminRole(AdminRole.CONTENT_ADMIN) || me.isSupport();
        if (!rating.getCustomerId().equals(me.id()) && !moderator) throw BusinessException.forbidden();
        Long providerId = rating.getProviderId();
        ratingRepository.delete(rating);
        ratingRepository.flush();
        refresh(providerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<RatingView> forProvider(Long providerId, Integer page, Integer size) {
        return PageResponse.of(ratingRepository.findByProviderId(providerId, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                RatingView::of);
    }

    @Transactional(readOnly = true)
    public RatingView forOrder(AuthUser me, Long orderId) {
        orderAccess.view(me, orderAccess.load(orderId));
        return ratingRepository.findByOrderId(orderId).map(RatingView::of).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
    }

    private void refresh(Long providerId) {
        Provider p = providerRepository.findByIdForUpdate(providerId).orElseThrow();
        Double avg = ratingRepository.averageScore(providerId);
        p.setRatingAvg(avg == null ? BigDecimal.ZERO : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        p.setRatingCount((int) ratingRepository.countByProviderId(providerId));
    }

    private Rating load(Long id) {
        return ratingRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
    }
}

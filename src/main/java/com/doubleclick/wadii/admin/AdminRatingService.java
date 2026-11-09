package com.doubleclick.wadii.admin;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.order.OrderQueryService;
import com.doubleclick.wadii.order.Rating;
import com.doubleclick.wadii.order.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/** Rating moderation: content admins hide abusive ratings; ratings are never deleted. */
@Service
@RequiredArgsConstructor
public class AdminRatingService {

    private final RatingRepository ratings;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<OrderQueryService.RatingView> list(Integer page, Integer size) {
        var result = ratings.findAllByOrderByIdDesc(PageResponse.pageable(page, size));
        Map<Long, String> names = users.findAllById(result.getContent().stream().map(Rating::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(result, r -> OrderQueryService.ratingView(r, names.get(r.getCustomerId())));
    }

    @Transactional
    public OrderQueryService.RatingView setHidden(Long id, boolean hidden) {
        Rating r = ratings.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        r.setHidden(hidden);
        audit.record(hidden ? "RATING_HIDDEN" : "RATING_UNHIDDEN", "RATING", id, null);
        return OrderQueryService.ratingView(r, users.findById(r.getCustomerId()).map(User::getFullName).orElse(""));
    }
}

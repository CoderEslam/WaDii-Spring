package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogServiceRepository;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.provider.dto.ProviderDtos.OfferRequest;
import com.doubleclick.wadii.provider.dto.ProviderDtos.OfferView;
import com.doubleclick.wadii.provider.dto.ProviderDtos.ProviderPublicView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Follows, promotional offers and saved offers. The acting user always comes from the JWT. */
@Service
@RequiredArgsConstructor
public class SocialService {

    private final FollowRepository followRepository;
    private final ProviderRepository providerRepository;
    private final ProviderOfferRepository offerRepository;
    private final SavedOfferRepository savedOfferRepository;
    private final CatalogServiceRepository catalogServiceRepository;
    private final ProviderAccess providerAccess;
    private final com.doubleclick.wadii.common.config.WadiiProperties properties;
    private final Clock clock;

    @Transactional
    public void follow(AuthUser me, Long providerId) {
        Provider p = providerRepository.findByIdForUpdate(providerId).orElseThrow(() -> BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND));
        if (p.isOwnedBy(me.id())) {
            throw BusinessException.validation("You cannot follow your own provider profile");
        }
        Follow.FollowId id = new Follow.FollowId(me.id(), providerId);
        if (followRepository.existsById(id)) {
            throw BusinessException.of(ErrorCode.ALREADY_FOLLOWING);
        }
        followRepository.save(new Follow(me.id(), providerId));
        p.setFollowersCount(p.getFollowersCount() + 1);
    }

    @Transactional
    public void unfollow(AuthUser me, Long providerId) {
        Provider p = providerRepository.findByIdForUpdate(providerId).orElseThrow(() -> BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND));
        Follow.FollowId id = new Follow.FollowId(me.id(), providerId);
        if (!followRepository.existsById(id)) {
            throw BusinessException.notFound(ErrorCode.NOT_FOUND);
        }
        followRepository.deleteById(id);
        p.setFollowersCount(Math.max(0, p.getFollowersCount() - 1));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProviderPublicView> following(AuthUser me, Integer page, Integer size) {
        Page<Follow> follows = followRepository.findByIdUserId(me.id(), PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(follows, f -> ProviderPublicView.of(providerRepository.findById(f.getId().getProviderId()).orElseThrow()));
    }

    // ---------------------------------------------------------------- promotional offers

    @Transactional
    public OfferView createOffer(AuthUser me, OfferRequest r) {
        Provider p = providerAccess.requireOwn(me);
        if (r.title() == null || r.title().isBlank() || r.description() == null || r.description().isBlank() || r.endDate() == null) {
            throw BusinessException.validation("title, description and endDate are required");
        }
        ProviderOffer o = new ProviderOffer();
        o.setProvider(p);
        applyOffer(o, r);
        return OfferView.of(offerRepository.save(o), null);
    }

    @Transactional
    public OfferView updateOffer(AuthUser me, Long offerId, OfferRequest r) {
        ProviderOffer o = ownOffer(me, offerId);
        applyOffer(o, r);
        return OfferView.of(o, null);
    }

    @Transactional
    public void deleteOffer(AuthUser me, Long offerId) {
        offerRepository.delete(ownOffer(me, offerId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferView> currentOffers(AuthUser me, Long serviceId, Long providerId, Integer page, Integer size) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        var pageable = PageResponse.pageable(page, size, Sort.by("endDate"));
        Page<ProviderOffer> result = providerId != null ? offerRepository.findByProviderId(providerId, pageable)
                : serviceId != null ? offerRepository.findCurrentByService(serviceId, today, pageable)
                : offerRepository.findCurrent(today, pageable);
        Set<Long> ids = new HashSet<>(result.map(ProviderOffer::getId).getContent());
        Set<Long> saved = ids.isEmpty() ? Set.of() : savedOfferRepository.findSavedOfferIds(me.id(), ids);
        return PageResponse.of(result, o -> OfferView.of(o, saved.contains(o.getId())));
    }

    @Transactional
    public void saveOffer(AuthUser me, Long offerId) {
        ProviderOffer o = offerRepository.findById(offerId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (savedOfferRepository.existsByUserIdAndOfferId(me.id(), offerId)) {
            throw BusinessException.of(ErrorCode.OFFER_ALREADY_SAVED);
        }
        SavedOffer s = new SavedOffer();
        s.setUserId(me.id());
        s.setOffer(o);
        savedOfferRepository.save(s);
    }

    @Transactional
    public void unsaveOffer(AuthUser me, Long offerId) {
        SavedOffer s = savedOfferRepository.findByUserIdAndOfferId(me.id(), offerId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        savedOfferRepository.delete(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferView> savedOffers(AuthUser me, Integer page, Integer size) {
        var result = savedOfferRepository.findByUserId(me.id(), PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, s -> OfferView.of(s.getOffer(), true));
    }

    private ProviderOffer ownOffer(AuthUser me, Long offerId) {
        Provider p = providerAccess.requireOwn(me);
        ProviderOffer o = offerRepository.findById(offerId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (!o.getProvider().getId().equals(p.getId())) {
            throw BusinessException.forbidden("This offer belongs to another provider");
        }
        return o;
    }

    private void applyOffer(ProviderOffer o, OfferRequest r) {
        if (r.title() != null) o.setTitle(r.title().trim());
        if (r.description() != null) o.setDescription(r.description().trim());
        if (r.endDate() != null) o.setEndDate(r.endDate());
        if (r.serviceIds() != null) {
            Set<Long> ids = new HashSet<>(r.serviceIds());
            List<CatalogService> services = catalogServiceRepository.findAllById(ids);
            if (services.size() != ids.size()) {
                throw BusinessException.validation("One or more service ids do not exist");
            }
            o.getServices().clear();
            o.getServices().addAll(services);
        }
    }
}

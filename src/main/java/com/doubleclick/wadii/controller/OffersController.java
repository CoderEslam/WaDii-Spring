package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.OfferDto;
import com.doubleclick.wadii.entities.Offer;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Service;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.OfferRepository;
import com.doubleclick.wadii.repository.SavedOfferRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import static org.springframework.security.core.context.SecurityContextHolder.getContext;

@RequiredArgsConstructor
@RestController
@RequestMapping("/offers")
public class OffersController extends Controller<Offer, OfferDto, Long> {

    private final OfferRepository offerRepository;
    private final SavedOfferRepository savedOfferRepository;
    private final ServiceRepository serviceRepository;
    private final CurrentUserService currentUserService;

    @Override
    public ResponseEntity<Response<Offer>> show(Long id) {
        return offerRepository.findById(id)
                .map(offer -> Response.response(offer, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no offer with this id : " + id, ResponseType.NOT_FOUND));
    }

    /** The offer always belongs to the authenticated provider; providerId in the body is ignored. */
    @Override
    public ResponseEntity<Response<Offer>> insert(Authentication authentication, @RequestBody OfferDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()
                || dto.getDescription() == null || dto.getDescription().trim().isEmpty()
                || dto.getEndDate() == null) {
            throw ApiException.badRequest("title, description and endDate are required");
        }
        Offer offer = new Offer();
        offer.setTitle(dto.getTitle().trim());
        offer.setDescription(dto.getDescription().trim());
        offer.setEndDate(dto.getEndDate());
        offer.setProvider(provider);
        if (dto.getServiceIds() != null && !dto.getServiceIds().isEmpty()) {
            offer.setServices(loadServices(dto.getServiceIds()));
        }
        offer = offerRepository.save(offer);
        return Response.response(offer, "Offer saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Offer>> update(Authentication authentication, OfferDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("offer id is required");
        }
        Offer offer = offerRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no offer with this id : " + dto.getId()));
        requireOwner(offer, provider);
        if (dto.getTitle() != null) offer.setTitle(dto.getTitle());
        if (dto.getDescription() != null) offer.setDescription(dto.getDescription());
        if (dto.getEndDate() != null) offer.setEndDate(dto.getEndDate());
        if (dto.getServiceIds() != null && !dto.getServiceIds().isEmpty()) {
            offer.setServices(loadServices(dto.getServiceIds()));
        }
        offer = offerRepository.save(offer);
        return Response.response(offer, "Offer updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Provider provider = currentUserService.provider(authentication);
        Offer offer = offerRepository.findById(id).orElse(null);
        if (offer == null) {
            return Response.response(false, "there is no offer with this id : " + id, ResponseType.NOT_FOUND);
        }
        requireOwner(offer, provider);
        offerRepository.deleteById(id);
        return Response.response(true, "offer deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Offer>>> readAll() {
        List<Offer> offers = offerRepository.findAll();
        markSaved(offers);
        return Response.response(offers, "All offers", ResponseType.SUCCESS);
    }

    @GetMapping("/filter-by-service/{serviceId}")
    public ResponseEntity<Response<List<Offer>>> filterByService(@PathVariable Long serviceId) {
        List<Offer> offers = offerRepository.findAllByServiceId(serviceId);
        markSaved(offers);
        return Response.response(offers, "Offers filtered by service", ResponseType.SUCCESS);
    }

    @GetMapping("/filter-by-services")
    public ResponseEntity<Response<List<Offer>>> filterByServices(@RequestParam List<Long> serviceIds) {
        List<Offer> offers = offerRepository.findAllByServiceIdIn(serviceIds);
        markSaved(offers);
        return Response.response(offers, "Offers filtered by services", ResponseType.SUCCESS);
    }

    private void requireOwner(Offer offer, Provider provider) {
        if (offer.getProvider() == null || !offer.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only manage your own offers");
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

    private void markSaved(List<Offer> offers) {
        Authentication authentication = getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String idStr) {
            Long userId = Long.parseLong(idStr);
            Set<Long> savedOfferIds = savedOfferRepository.findOfferIdsByUserId(userId);
            offers.forEach(offer -> offer.setSaved(savedOfferIds.contains(offer.getId())));
        }
    }
}

package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.dto.OfferDto;
import com.doubleclick.wadii.entities.Offer;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.repository.OfferRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.SavedOfferRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/offers")
public class OffersController extends Controller<Offer, OfferDto, Long> {

    private final OfferRepository offerRepository;
    private final ProviderRepository providerRepository;
    private final SavedOfferRepository savedOfferRepository;

    @Override
    public ResponseEntity<Response<Offer>> show(Long id) {
        return offerRepository.findById(id)
                .map(offer -> Response.response(offer, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no offer with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Offer>> insert(Authentication authentication, @RequestBody OfferDto offerDto) {
        if (offerDto.isNotEmpty()) {
            Optional<Provider> provinceOptional = providerRepository.findById(offerDto.getProviderId());
            if (provinceOptional.isPresent()) {
                Offer offer = new Offer();
                offer.setDescription(offerDto.getDescription());
                offer.setTitle(offerDto.getTitle());
                offer.setEndDate(offerDto.getEndDate());
                offer.setProvider(provinceOptional.get());
                offer = offerRepository.save(offer);
                return Response.response(offer, "Offer saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is no provider with this id : " + offerDto.getProviderId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name or province id is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Offer>> update(OfferDto offerDto) {
        if (offerDto.isNotEmpty()) {
            Optional<Offer> offerOptional = offerRepository.findById(offerDto.getId());
            if (offerOptional.isPresent()) {
                Optional<Provider> provinceOptional = providerRepository.findById(offerDto.getProviderId());
                if (provinceOptional.isPresent()) {
                    Offer offer = offerOptional.get();
                    offer.setId(offerDto.getId());
                    offer.setDescription(offerDto.getDescription());
                    offer.setTitle(offerDto.getTitle());
                    offer.setEndDate(offerDto.getEndDate());
                    offer.setProvider(provinceOptional.get());
                    offer = offerRepository.save(offer);
                    return Response.response(offer, "Offer saved successfully", ResponseType.SUCCESS);
                } else {
                    return Response.response(null, "there is no provider with this id : " + offerDto.getProviderId(), ResponseType.NOT_FOUND);
                }
            } else {
                return Response.response(null, "there is no offer with this id : " + offerDto.getId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name or description or provider id or end date is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Offer>> delete(Long id) {
        Optional<Offer> offerOptional = offerRepository.findById(id);
        if (offerOptional.isPresent()) {
            offerRepository.deleteById(id);
            return Response.response(null, "offer deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no offer with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Offer>>> readAll() {
        List<Offer> offers = offerRepository.findAll();
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            Set<Long> savedOfferIds = savedOfferRepository.findOfferIdsByUserId(user.getId());
            offers.forEach(offer -> offer.setSaved(savedOfferIds.contains(offer.getId())));
        }
        return Response.response(offers, "All offers", ResponseType.SUCCESS);
    }
}

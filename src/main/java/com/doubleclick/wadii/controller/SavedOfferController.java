package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.SavedOfferDto;
import com.doubleclick.wadii.entities.Offer;
import com.doubleclick.wadii.entities.SavedOffer;
import com.doubleclick.wadii.repository.OfferRepository;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/saved-offers")
public class SavedOfferController extends Controller<SavedOffer, SavedOfferDto, Long> {

    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final SavedOfferRepository savedOfferRepository;

    @Override
    public ResponseEntity<Response<SavedOffer>> show(Long id) {
        return savedOfferRepository.findById(id)
                .map(savedOffer -> Response.response(savedOffer, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No saved offer found with id: " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<SavedOffer>> insert(Authentication authentication, @RequestBody SavedOfferDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not found", ResponseType.NOT_FOUND);
        }
        Optional<Offer> offerOptional = offerRepository.findById(dto.getOfferId());
        if (offerOptional.isEmpty()) {
            return Response.response(null, "No offer found with id: " + dto.getOfferId(), ResponseType.NOT_FOUND);
        }
        if (savedOfferRepository.existsByUserIdAndOfferId(userOptional.get().getId(), dto.getOfferId())) {
            return Response.response(null, "Offer already saved", ResponseType.ERROR);
        }
        SavedOffer savedOffer = new SavedOffer();
        savedOffer.setUser(userOptional.get());
        savedOffer.setOffer(offerOptional.get());
        savedOffer = savedOfferRepository.save(savedOffer);
        return Response.response(savedOffer, "Offer saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<SavedOffer>> update(Authentication authentication, SavedOfferDto dto) {
        return Response.response(null, "Not supported", ResponseType.ERROR);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        if (!savedOfferRepository.existsById(id)) {
            return Response.response(false, "No saved offer found with id: " + id, ResponseType.NOT_FOUND);
        }
        savedOfferRepository.deleteById(id);
        return Response.response(true, "Saved offer removed successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<SavedOffer>>> readAll() {
        return Response.response(savedOfferRepository.findAll(), "All saved offers", ResponseType.SUCCESS);
    }

    @GetMapping("/my-saved-offers")
    public ResponseEntity<Response<List<SavedOffer>>> getMySavedOffers(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not found", ResponseType.NOT_FOUND);
        }
        List<SavedOffer> savedOffers = savedOfferRepository.findByUserId(userOptional.get().getId());
        return Response.response(savedOffers, "Saved offers", ResponseType.SUCCESS);
    }

    @DeleteMapping("/remove/{offerId}")
    public ResponseEntity<Response<Boolean>> removeSavedOffer(Authentication authentication, @PathVariable Long offerId) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(false, "User not found", ResponseType.NOT_FOUND);
        }
        if (!savedOfferRepository.existsByUserIdAndOfferId(userOptional.get().getId(), offerId)) {
            return Response.response(false, "Offer is not saved", ResponseType.NOT_FOUND);
        }
        savedOfferRepository.deleteByUserIdAndOfferId(userOptional.get().getId(), offerId);
        return Response.response(true, "Saved offer removed successfully", ResponseType.SUCCESS);
    }
}

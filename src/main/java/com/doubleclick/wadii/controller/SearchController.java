package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.SearchResultDto;
import com.doubleclick.wadii.entities.Offer;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.repository.OfferRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.SavedOfferRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/search")
public class SearchController {

    private final OfferRepository offerRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;
    private final SavedOfferRepository savedOfferRepository;
    private final BranchRepository branchRepository;

    @GetMapping
    public ResponseEntity<Response<SearchResultDto>> search(
            @RequestParam String q,
            Authentication authentication) {

        if (q == null || q.trim().isEmpty()) {
            return Response.response(null, "Search query is required", ResponseType.ERROR);
        }
        List<Offer> offers = offerRepository.search(q.trim());
        if (authentication != null && authentication.getCredentials() instanceof String idStr) {
            Long userId = Long.parseLong(idStr);
            Set<Long> savedOfferIds = savedOfferRepository.findOfferIdsByUserId(userId);
            offers.forEach(offer -> offer.setSaved(savedOfferIds.contains(offer.getId())));
        }
        String query = q.trim();
        SearchResultDto result = new SearchResultDto(
                offers,
                serviceRepository.findByNameContainingIgnoreCase(query),
                providerRepository.findByNameContainingIgnoreCase(query),
                branchRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(query, query)
        );
        return Response.response(result, "Search results", ResponseType.SUCCESS);
    }
}

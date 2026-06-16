package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.AdvertisementDto;
import com.doubleclick.wadii.entities.Advertisement;
import com.doubleclick.wadii.entities.AdvertisementStatus;
import com.doubleclick.wadii.repository.AdvertisementRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/advertisements")
public class AdvertisementController extends Controller<Advertisement, AdvertisementDto, Long> {

    private final AdvertisementRepository advertisementRepository;

    @Override
    public ResponseEntity<Response<Advertisement>> show(Long id) {
        return advertisementRepository.findById(id)
                .map(ad -> Response.response(ad, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No advertisement found with id: " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Advertisement>> insert(Authentication authentication, @RequestBody AdvertisementDto dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            return Response.response(null, "Advertisement title is required", ResponseType.ERROR);
        }
        Advertisement advertisement = new Advertisement();
        advertisement.setTitle(dto.getTitle());
        advertisement.setDescription(dto.getDescription());
        advertisement.setImageUrl(dto.getImageUrl());
        advertisement.setTargetUrl(dto.getTargetUrl());
        advertisement.setAdvertiserName(dto.getAdvertiserName());
        advertisement.setPriority(dto.getPriority());
        advertisement.setStartDate(dto.getStartDate());
        advertisement.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null) advertisement.setStatus(dto.getStatus());
        advertisement = advertisementRepository.save(advertisement);
        return Response.response(advertisement, "Advertisement created successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Advertisement>> update(@RequestBody AdvertisementDto dto) {
        if (dto.getId() == null) {
            return Response.response(null, "Advertisement id is required", ResponseType.ERROR);
        }
        Optional<Advertisement> optional = advertisementRepository.findById(dto.getId());
        if (optional.isEmpty()) {
            return Response.response(null, "No advertisement found with id: " + dto.getId(), ResponseType.NOT_FOUND);
        }
        Advertisement advertisement = optional.get();
        if (dto.getTitle() != null) advertisement.setTitle(dto.getTitle());
        if (dto.getDescription() != null) advertisement.setDescription(dto.getDescription());
        if (dto.getImageUrl() != null) advertisement.setImageUrl(dto.getImageUrl());
        if (dto.getTargetUrl() != null) advertisement.setTargetUrl(dto.getTargetUrl());
        if (dto.getAdvertiserName() != null) advertisement.setAdvertiserName(dto.getAdvertiserName());
        if (dto.getStatus() != null) advertisement.setStatus(dto.getStatus());
        if (dto.getPriority() != null) advertisement.setPriority(dto.getPriority());
        if (dto.getStartDate() != null) advertisement.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) advertisement.setEndDate(dto.getEndDate());
        advertisement = advertisementRepository.save(advertisement);
        return Response.response(advertisement, "Advertisement updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Advertisement>> delete(Long id) {
        if (!advertisementRepository.existsById(id)) {
            return Response.response(null, "No advertisement found with id: " + id, ResponseType.NOT_FOUND);
        }
        advertisementRepository.deleteById(id);
        return Response.response(null, "Advertisement deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Advertisement>>> readAll() {
        return Response.response(advertisementRepository.findAllByOrderByPriorityDesc(), "All advertisements", ResponseType.SUCCESS);
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<Response<List<Advertisement>>> getByStatus(@PathVariable AdvertisementStatus status) {
        return Response.response(advertisementRepository.findByStatus(status), "Advertisements by status", ResponseType.SUCCESS);
    }

    @GetMapping("/active")
    public ResponseEntity<Response<List<Advertisement>>> getActive() {
        return Response.response(advertisementRepository.findActiveAdvertisements(LocalDateTime.now()), "Active advertisements", ResponseType.SUCCESS);
    }

    @GetMapping("/by-advertiser/{advertiserName}")
    public ResponseEntity<Response<List<Advertisement>>> getByAdvertiser(@PathVariable String advertiserName) {
        return Response.response(advertisementRepository.findByAdvertiserName(advertiserName), "Advertisements by advertiser", ResponseType.SUCCESS);
    }

    @PostMapping("/track-impression/{id}")
    public ResponseEntity<Response<Advertisement>> trackImpression(@PathVariable Long id) {
        Optional<Advertisement> optional = advertisementRepository.findById(id);
        if (optional.isEmpty()) {
            return Response.response(null, "No advertisement found with id: " + id, ResponseType.NOT_FOUND);
        }
        Advertisement advertisement = optional.get();
        advertisement.setImpressions(advertisement.getImpressions() + 1);
        advertisement = advertisementRepository.save(advertisement);
        return Response.response(advertisement, "Impression tracked", ResponseType.SUCCESS);
    }

    @PostMapping("/track-click/{id}")
    public ResponseEntity<Response<Advertisement>> trackClick(@PathVariable Long id) {
        Optional<Advertisement> optional = advertisementRepository.findById(id);
        if (optional.isEmpty()) {
            return Response.response(null, "No advertisement found with id: " + id, ResponseType.NOT_FOUND);
        }
        Advertisement advertisement = optional.get();
        advertisement.setClicks(advertisement.getClicks() + 1);
        advertisement = advertisementRepository.save(advertisement);
        return Response.response(advertisement, "Click tracked", ResponseType.SUCCESS);
    }
}

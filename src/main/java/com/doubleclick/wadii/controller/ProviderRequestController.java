package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.ProviderRequestDto;
import com.doubleclick.wadii.entities.*;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ProviderRequestRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.service.FileStorageService;
import com.doubleclick.wadii.service.UserNotifier;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/provider-requests")
public class ProviderRequestController {

    private final ProviderRequestRepository providerRequestRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final UserNotifier userNotifier;

    /** The applicant is always the authenticated user; userId in the form is ignored. */
    @PostMapping(value = "/request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Response<ProviderRequest>> requestToBeProvider(Authentication authentication, @ModelAttribute ProviderRequestDto dto) {
        User user = currentUserService.user(authentication);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("Admins cannot apply to become providers");
        }
        if (user.getRole() == Role.PROVIDER || providerRepository.findByUserId(user.getId()).isPresent()) {
            throw ApiException.conflict("This user is already a provider");
        }
        if (providerRequestRepository.existsByUserId(user.getId())) {
            throw ApiException.conflict("A request already exists for this user");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw ApiException.badRequest("name is required");
        }

        ProviderRequest request = new ProviderRequest();
        request.setName(dto.getName().trim());
        request.setAddress(dto.getAddress());
        request.setPhoneNumber(dto.getPhoneNumber());
        request.setUser(user);
        if (dto.getServiceIds() != null && !dto.getServiceIds().isEmpty()) {
            List<Long> ids = dto.getServiceIds().stream().distinct().toList();
            List<Service> services = serviceRepository.findAllById(ids);
            if (services.size() != ids.size()) {
                throw ApiException.notFound("One or more service IDs are invalid");
            }
            request.setServices(services);
        }
        if (dto.getLinks() != null && !dto.getLinks().isEmpty()) {
            request.setLinks(dto.getLinks());
        }
        // Verification documents go to the private store, which no public endpoint serves.
        if (dto.getFrontIdImage() != null && !dto.getFrontIdImage().isEmpty()) {
            request.setFrontIdImage(fileStorageService.storePrivateImage(dto.getFrontIdImage()));
        }
        if (dto.getBackIdImage() != null && !dto.getBackIdImage().isEmpty()) {
            request.setBackIdImage(fileStorageService.storePrivateImage(dto.getBackIdImage()));
        }
        if (dto.getTaxCardFront() != null && !dto.getTaxCardFront().isEmpty()) {
            request.setTaxCardFront(fileStorageService.storePrivateImage(dto.getTaxCardFront()));
        }
        if (dto.getTaxCardBack() != null && !dto.getTaxCardBack().isEmpty()) {
            request.setTaxCardBack(fileStorageService.storePrivateImage(dto.getTaxCardBack()));
        }
        request = providerRequestRepository.save(request);
        return Response.response(request, "Request submitted successfully", ResponseType.SUCCESS);
    }

    @GetMapping("/show-all")
    public ResponseEntity<Response<List<ProviderRequest>>> getAllRequests(Authentication authentication) {
        currentUserService.admin(authentication);
        return Response.response(providerRequestRepository.findAll(), "All provider requests", ResponseType.SUCCESS);
    }

    /** Verification document of a request. type: front-id | back-id | tax-front | tax-back. Admin or the applicant only. */
    @GetMapping("/{id}/document/{type}")
    public ResponseEntity<Resource> getDocument(Authentication authentication, @PathVariable Long id, @PathVariable String type) {
        User caller = currentUserService.user(authentication);
        ProviderRequest request = providerRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No request found with id: " + id));
        if (!currentUserService.isAdmin(caller) && !request.getUser().getId().equals(caller.getId())) {
            throw ApiException.forbidden("You are not allowed to access this document");
        }
        String filename = switch (type) {
            case "front-id" -> request.getFrontIdImage();
            case "back-id" -> request.getBackIdImage();
            case "tax-front" -> request.getTaxCardFront();
            case "tax-back" -> request.getTaxCardBack();
            default -> throw ApiException.badRequest("type must be front-id, back-id, tax-front or tax-back");
        };
        if (filename == null) {
            throw ApiException.notFound("This document was not uploaded");
        }
        Resource resource = fileStorageService.loadPrivate(filename);
        String contentType = null;
        try {
            contentType = Files.probeContentType(resource.getFile().toPath());
        } catch (IOException ignored) {
            // fall back to octet-stream
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @PostMapping("/accept/{id}")
    @Transactional
    public ResponseEntity<Response<Provider>> acceptRequest(Authentication authentication, @PathVariable Long id) {
        currentUserService.admin(authentication);
        ProviderRequest providerRequest = providerRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No request found with id: " + id));
        User user = providerRequest.getUser();
        if (providerRepository.findByUserId(user.getId()).isPresent()) {
            throw ApiException.conflict("This user is already a provider");
        }

        Provider provider = new Provider();
        provider.setName(providerRequest.getName());
        provider.setUser(user);
        provider.setFollowersCount(0L);
        provider.setRate(0.0);
        provider = providerRepository.save(provider);

        if (providerRequest.getLinks() != null) {
            List<Links> linkEntities = new ArrayList<>();
            for (String linkUrl : providerRequest.getLinks()) {
                Links link = new Links();
                link.setLink(linkUrl);
                link.setProvider(provider);
                linkEntities.add(link);
            }
            provider.setLinks(linkEntities);
            providerRepository.save(provider);
        }
        if (providerRequest.getServices() != null) {
            for (Service service : providerRequest.getServices()) {
                if (service.getProviders() == null) {
                    service.setProviders(new ArrayList<>());
                }
                service.getProviders().add(provider);
            }
            serviceRepository.saveAll(providerRequest.getServices());
        }
        user.setRole(Role.PROVIDER);
        user.setProvider(provider);
        userRepository.save(user);
        providerRequestRepository.deleteById(id);
        userNotifier.notify(user, "Provider Request Accepted",
                "Congratulations! Your provider request has been accepted.", "PROVIDER_ACCEPTED", null);
        return Response.response(provider, "Request accepted, provider created successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/reject/{id}")
    @Transactional
    public ResponseEntity<Response<ProviderRequest>> rejectRequest(Authentication authentication, @PathVariable Long id, @RequestParam(required = false) String reason) {
        currentUserService.admin(authentication);
        ProviderRequest providerRequest = providerRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No request found with id: " + id));
        User user = providerRequest.getUser();
        providerRequestRepository.deleteById(id);
        String message = "Your provider request has been rejected.";
        if (reason != null && !reason.isBlank()) {
            message += " Reason: " + reason;
        }
        userNotifier.notify(user, "Provider Request Rejected", message, "PROVIDER_REJECTED", null);
        return Response.response(providerRequest, "Request rejected successfully", ResponseType.SUCCESS);
    }

    /** Admin only: provider -> user. The provider profile is kept. */
    @PostMapping("/put-it-user/{userId}")
    public ResponseEntity<Response<User>> revertProviderToUser(Authentication authentication, @PathVariable Long userId) {
        currentUserService.admin(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("No user found with id: " + userId));
        if (user.getRole() != Role.PROVIDER) {
            throw ApiException.badRequest("User is not a provider");
        }
        user.setRole(Role.USER);
        userRepository.save(user);
        return Response.response(user, "Provider reverted to user successfully", ResponseType.SUCCESS);
    }

    /** Admin only: user -> provider, only if a provider profile already exists for the user. */
    @PostMapping("/put-it-provider/{userId}")
    public ResponseEntity<Response<User>> revertUserToProvider(Authentication authentication, @PathVariable Long userId) {
        currentUserService.admin(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("No user found with id: " + userId));
        if (user.getRole() != Role.USER) {
            throw ApiException.badRequest("User is not a User");
        }
        Provider provider = providerRepository.findByUserId(userId).orElse(null);
        if (provider == null) {
            return Response.response(user, "you can't put it provider because there is no id of provider binding with it", ResponseType.SUCCESS);
        }
        user.setProvider(provider);
        user.setRole(Role.PROVIDER);
        userRepository.save(user);
        return Response.response(user, "User promoted to provider successfully", ResponseType.SUCCESS);
    }
}

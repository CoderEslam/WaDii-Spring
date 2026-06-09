package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ProviderRequestDto;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.ProviderRequest;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ProviderRequestRepository;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/provider-requests")
public class ProviderRequestController {

    private final ProviderRequestRepository providerRequestRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    @PostMapping("/request")
    public ResponseEntity<Response<ProviderRequest>> requestToBeProvider(@RequestBody ProviderRequestDto dto) {
        Optional<User> userOptional = userRepository.findById(dto.getUserId());
        if (userOptional.isEmpty()) {
            return Response.response(null, "No user found with id: " + dto.getUserId(), ResponseType.NOT_FOUND);
        }
        if (providerRequestRepository.existsByUserId(dto.getUserId())) {
            return Response.response(null, "A request already exists for this user", ResponseType.ERROR);
        }
        if (providerRepository.findByUserId(dto.getUserId()).isPresent()) {
            return Response.response(null, "This user is already a provider", ResponseType.ERROR);
        }
        ProviderRequest request = new ProviderRequest();
        request.setName(dto.getName());
        request.setUser(userOptional.get());
        request = providerRequestRepository.save(request);
        return Response.response(request, "Request submitted successfully", ResponseType.SUCCESS);
    }

    @GetMapping("/show-all")
    public ResponseEntity<Response<List<ProviderRequest>>> getAllRequests() {
        return Response.response(providerRequestRepository.findAll(), "All provider requests", ResponseType.SUCCESS);
    }

    @PostMapping("/accept/{id}")
    public ResponseEntity<Response<Provider>> acceptRequest(@PathVariable Long id) {
        Optional<ProviderRequest> requestOptional = providerRequestRepository.findById(id);
        if (requestOptional.isEmpty()) {
            return Response.response(null, "No request found with id: " + id, ResponseType.NOT_FOUND);
        }
        ProviderRequest providerRequest = requestOptional.get();
        User user = providerRequest.getUser();

        Provider provider = new Provider();
        provider.setName(providerRequest.getName());
        provider.setUser(user);
        provider.setFollowersCount(0L);
        provider.setRate(0.0);
        provider = providerRepository.save(provider);

        user.setRole(Role.PROVIDER);
        user.setProvider(provider);
        userRepository.save(user);

        providerRequestRepository.deleteById(id);

        return Response.response(provider, "Request accepted, provider created successfully", ResponseType.SUCCESS);
    }
}
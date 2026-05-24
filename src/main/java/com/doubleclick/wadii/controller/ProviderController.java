package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ProviderDto;
import com.doubleclick.wadii.dto.ServicesProviderDto;
import com.doubleclick.wadii.entities.Follower;
import com.doubleclick.wadii.entities.FollowerId;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Service;
import org.springframework.web.bind.annotation.RequestBody;
import com.doubleclick.wadii.repository.FollowersRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
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
@RequestMapping("/providers")
public class ProviderController extends Controller<Provider, ProviderDto, Long> {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;
    private final FollowersRepository followersRepository;

    @Override
    public ResponseEntity<Response<Provider>> show(Long id) {
        return providerRepository.findById(id)
                .map(provider -> Response.response(provider, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no city with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Provider>> insert(Authentication authentication, @RequestBody ProviderDto providerDto) {
        Optional<User> userOptional = userRepository.findById(providerDto.getUserId());
        if (userOptional.isPresent()) {
            Provider provider = new Provider();
            provider.setUser(userOptional.get());
            provider.setFollowersCount(0L);
            provider.setRate(0.0);
            provider = providerRepository.save(provider);
            return Response.response(provider, "provider saved successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no user with this id : " + providerDto.getUserId(), ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<Provider>> update(ProviderDto providerDto) {
        return null;
    }

    @Override
    public ResponseEntity<Response<Provider>> delete(Long id) {
        Optional<Provider> providerOptional = providerRepository.findById(id);
        if (providerOptional.isPresent()) {
            providerRepository.deleteById(id);
            return Response.response(null, "provider deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no provider with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Provider>>> readAll() {
        return Response.response(providerRepository.findAll(), "All providers", ResponseType.SUCCESS);
    }


    public ResponseEntity<Response<Provider>> getProviderByUserId(Long userId) {
        Optional<Provider> providerOptional = providerRepository.findByUserId(userId);
        if (providerOptional.isPresent()) {
            return Response.response(providerOptional.get(), "Provider found", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "No provider found for user id: " + userId, ResponseType.NOT_FOUND);
        }
    }

    @PostMapping("/update-services")
    public ResponseEntity<Response<Provider>> storeServicesForProvider(@RequestBody ServicesProviderDto servicesProviderDto) {
        Optional<Provider> providerOptional = providerRepository.findById(servicesProviderDto.getProviderId());
        if (providerOptional.isPresent()) {
            Provider provider = providerOptional.get();
            // Step 1: Remove current provider from old services
            List<Service> oldServices = provider.getServices();
            for (Service oldService : oldServices) {
                oldService.getProviders().remove(provider);
                serviceRepository.save(oldService);
            }
            // Step 2: Fetch new services
            List<Service> newServices = serviceRepository.findAllById(servicesProviderDto.getServiceIds());
            // Step 3: Add provider to new services
            for (Service newService : newServices) {
                newService.getProviders().add(provider);
                serviceRepository.save(newService);
            }
            // Step 4: Update provider's service list
            provider.setServices(newServices);
            provider = providerRepository.save(provider);
            return Response.response(provider, "Services updated successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "No provider found with id: " + servicesProviderDto.getProviderId(), ResponseType.NOT_FOUND);
        }
    }

    @PostMapping("/follow-provider/{id}")
    public ResponseEntity<Response<Follower>> follow(Authentication authentication, @PathVariable Long id) {
        Optional<Provider> providerOptional = providerRepository.findById(id);
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isPresent()) {
            if (providerOptional.isPresent()) {
                Provider provider = providerOptional.get();
                Long count = provider.getFollowersCount();
                provider.setFollowersCount(++count);
                Follower follower = new Follower();
                FollowerId followerId = new FollowerId(userOptional.get().getId(), id);
                follower.setId(followerId);
                follower.setProvider(provider);
                follower.setUser(userOptional.get());
                follower = followersRepository.save(follower);
                provider = providerRepository.save(provider);
                return Response.response(follower, "Done", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "provider id not exist", ResponseType.SUCCESS);
            }
        } else {
            return Response.response(null, "user id not exist", ResponseType.SUCCESS);
        }
    }
}

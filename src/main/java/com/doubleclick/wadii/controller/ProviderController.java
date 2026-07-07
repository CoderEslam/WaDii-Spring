package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ProviderDto;
import com.doubleclick.wadii.dto.ServicesProviderDto;
import com.doubleclick.wadii.dto.UpdateProviderDto;
import com.doubleclick.wadii.entities.*;
import org.springframework.web.bind.annotation.RequestBody;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.repository.FollowersRepository;
import com.doubleclick.wadii.repository.LinksRepository;
import com.doubleclick.wadii.repository.OfferRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.repository.WorkTimeRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final LinksRepository linksRepository;
    private final OfferRepository offerRepository;
    private final BranchRepository branchRepository;
    private final WorkTimeRepository workTimeRepository;

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
    public ResponseEntity<Response<Provider>> update(Authentication authentication, ProviderDto providerDto) {
        return null;
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Optional<Provider> providerOptional = providerRepository.findById(id);
        if (providerOptional.isPresent()) {
            providerRepository.deleteById(id);
            return Response.response(true, "provider deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(false, "there is no provider with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Provider>>> readAll() {
        return Response.response(providerRepository.findAll(), "All providers", ResponseType.SUCCESS);
    }

    @GetMapping("/me")
    public ResponseEntity<Response<Provider>> getMyProviderProfile(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not found", ResponseType.NOT_FOUND);
        }
        return providerRepository.findByUserId(userOptional.get().getId())
                .map(provider -> Response.response(provider, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No provider found for this user", ResponseType.NOT_FOUND));
    }

    @GetMapping("/filter-by-service/{serviceId}")
    public ResponseEntity<Response<List<Provider>>> filterByService(@PathVariable Long serviceId) {
        List<Provider> providers = providerRepository.findAllByServiceId(serviceId);
        return Response.response(providers, "Providers filtered by service", ResponseType.SUCCESS);
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
                count = count + 1;
                provider.setFollowersCount(count);
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

    @PostMapping("/update-all/{id}")
    public ResponseEntity<Response<Provider>> updateAll(@PathVariable Long id, @RequestBody UpdateProviderDto dto) {
        Optional<Provider> providerOptional = providerRepository.findById(id);
        if (providerOptional.isEmpty()) {
            return Response.response(null, "No provider found with id: " + id, ResponseType.NOT_FOUND);
        }

        Provider provider = providerOptional.get();

        // Update user basic info
        User user = provider.getUser();
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        userRepository.save(user);

        // Update services
        if (dto.getServiceIds() != null) {
            List<Service> oldServices = provider.getServices();
            for (Service s : oldServices) {
                s.getProviders().remove(provider);
                serviceRepository.save(s);
            }
            List<Service> newServices = serviceRepository.findAllById(dto.getServiceIds());
            for (Service s : newServices) {
                s.getProviders().add(provider);
                serviceRepository.save(s);
            }
            provider.setServices(newServices);
        }

        // Update branches and their work times
        if (dto.getBranches() != null) {
            for (UpdateProviderDto.Branch b : dto.getBranches()) {
                Branch branch;
                if (b.getId() > 0) {
                    branch = branchRepository.findById((long) b.getId()).orElse(new Branch());
                } else {
                    branch = new Branch();
                }
                if (b.getName() != null) branch.setName(b.getName());
                if (b.getAddress() != null) branch.setAddress(b.getAddress());
                branch.setProvider(provider);
                branch = branchRepository.save(branch);
                if (b.getWorkTimes() != null) {
                    final Branch savedBranch = branch;
                    for (UpdateProviderDto.WorkTime wt : b.getWorkTimes()) {
                        if (wt.getId() > 0) {
                            workTimeRepository.findById((long) wt.getId()).ifPresent(workTime -> {
                                workTime.setDay(wt.getDay());
                                workTime.setStartTime(wt.getStartTime());
                                workTime.setCloseTime(wt.getCloseTime());
                                workTimeRepository.save(workTime);
                            });
                        } else {
                            WorkTime workTime = workTimeRepository
                                    .findByBranchIdAndDay(savedBranch.getId(), wt.getDay())
                                    .orElse(new WorkTime());
                            workTime.setDay(wt.getDay());
                            workTime.setStartTime(wt.getStartTime());
                            workTime.setCloseTime(wt.getCloseTime());
                            workTime.setBranch(savedBranch);
                            workTimeRepository.save(workTime);
                        }
                    }
                }
            }
        }

        // Update links
        if (dto.getLinks() != null) {
            for (UpdateProviderDto.Link l : dto.getLinks()) {
                if (l.getId() > 0) {
                    linksRepository.findById((long) l.getId()).ifPresent(link -> {
                        link.setLink(l.getLink());
                        linksRepository.save(link);
                    });
                } else {
                    Links newLink = new Links();
                    newLink.setLink(l.getLink());
                    newLink.setProvider(provider);
                    linksRepository.save(newLink);
                }
            }
        }

        // Update offers
        if (dto.getOffers() != null) {
            for (UpdateProviderDto.Offer o : dto.getOffers()) {
                if (o.getId() > 0) {
                    offerRepository.findById((long) o.getId()).ifPresent(offer -> {
                        offer.setTitle(o.getTitle());
                        offer.setDescription(o.getDescription());
                        if (o.getEndDate() != null) offer.setEndDate(LocalDate.parse(o.getEndDate()));
                        offerRepository.save(offer);
                    });
                } else {
                    Offer newOffer = new Offer();
                    newOffer.setTitle(o.getTitle());
                    newOffer.setDescription(o.getDescription());
                    if (o.getEndDate() != null) newOffer.setEndDate(LocalDate.parse(o.getEndDate()));
                    newOffer.setProvider(provider);
                    offerRepository.save(newOffer);
                }
            }
        }

        provider = providerRepository.save(provider);
        return Response.response(provider, "Provider updated successfully", ResponseType.SUCCESS);
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<Response<List<Follower>>> getFollowers(@PathVariable Long id) {
        if (!providerRepository.existsById(id)) {
            return Response.response(null, "No provider found with id: " + id, ResponseType.NOT_FOUND);
        }
        List<Follower> followers = followersRepository.findByProviderId(id);
        return Response.response(followers, "Followers list", ResponseType.SUCCESS);
    }

    @DeleteMapping("/unfollow-provider/{id}")
    public ResponseEntity<Response<Boolean>> unfollow(Authentication authentication, @PathVariable Long id) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        Optional<Provider> providerOptional = providerRepository.findById(id);
        if (userOptional.isEmpty()) {
            return Response.response(false, "user not found", ResponseType.NOT_FOUND);
        }
        if (providerOptional.isEmpty()) {
            return Response.response(false, "provider id not exist", ResponseType.NOT_FOUND);
        }
        FollowerId followerId = new FollowerId(userOptional.get().getId(), id);
        if (!followersRepository.existsById(followerId)) {
            return Response.response(false, "you are not following this provider", ResponseType.NOT_FOUND);
        }
        followersRepository.deleteById(followerId);
        Provider provider = providerOptional.get();
        long count = provider.getFollowersCount();
        provider.setFollowersCount(count > 0 ? count - 1 : 0);
        providerRepository.save(provider);
        return Response.response(true, "unfollowed successfully", ResponseType.SUCCESS);
    }
}

package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.CreateProviderDto;
import com.doubleclick.wadii.dto.ProviderDto;
import com.doubleclick.wadii.dto.ServicesProviderDto;
import com.doubleclick.wadii.dto.UpdateProviderDto;
import com.doubleclick.wadii.entities.*;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.repository.CityRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final CityRepository cityRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    // ---------------------------------------------------------------- read

    @Override
    public ResponseEntity<Response<Provider>> show(Long id) {
        return providerRepository.findById(id)
                .map(provider -> Response.response(provider, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no provider with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<List<Provider>>> readAll() {
        return Response.response(providerRepository.findAll(), "All providers", ResponseType.SUCCESS);
    }

    @GetMapping("/me")
    public ResponseEntity<Response<Provider>> getMyProviderProfile(Authentication authentication) {
        User user = currentUserService.user(authentication);
        return providerRepository.findByUserId(user.getId())
                .map(provider -> Response.response(provider, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No provider found for this user", ResponseType.NOT_FOUND));
    }

    @GetMapping("/filter-by-service/{serviceId}")
    public ResponseEntity<Response<List<Provider>>> filterByService(@PathVariable Long serviceId) {
        List<Provider> providers = providerRepository.findAllByServiceId(serviceId);
        return Response.response(providers, "Providers filtered by service", ResponseType.SUCCESS);
    }

    @GetMapping("/filter-by-services")
    public ResponseEntity<Response<List<Provider>>> filterByServices(@RequestParam List<Long> serviceIds) {
        List<Provider> providers = providerRepository.findAllByServiceIdIn(serviceIds);
        return Response.response(providers, "Providers filtered by services", ResponseType.SUCCESS);
    }

    /** Followers contain user contact details, so only the provider and admins can list them. */
    @GetMapping("/{id}/followers")
    public ResponseEntity<Response<List<Follower>>> getFollowers(Authentication authentication, @PathVariable Long id) {
        User caller = currentUserService.user(authentication);
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No provider found with id: " + id));
        currentUserService.requireProviderOwnerOrAdmin(caller, provider);
        return Response.response(followersRepository.findByProviderId(id), "Followers list", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- admin: create / delete / status

    /** Admin only. Prefer /providers/create-by-admin, which also creates the account. */
    @Override
    @Transactional
    public ResponseEntity<Response<Provider>> insert(Authentication authentication, @RequestBody ProviderDto dto) {
        currentUserService.admin(authentication);
        if (dto.getUserId() == null) {
            throw ApiException.badRequest("userId is required");
        }
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> ApiException.notFound("there is no user with this id : " + dto.getUserId()));
        if (providerRepository.findByUserId(user.getId()).isPresent()) {
            throw ApiException.conflict("This user is already a provider");
        }
        Provider provider = new Provider();
        provider.setUser(user);
        provider.setName(((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim());
        provider.setFollowersCount(0L);
        provider.setRate(0.0);
        provider = providerRepository.save(provider);
        user.setRole(Role.PROVIDER);
        userRepository.save(user);
        return Response.response(provider, "provider saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        currentUserService.admin(authentication);
        if (!providerRepository.existsById(id)) {
            return Response.response(false, "there is no provider with this id : " + id, ResponseType.NOT_FOUND);
        }
        providerRepository.deleteById(id);
        return Response.response(true, "provider deleted successfully", ResponseType.SUCCESS);
    }

    /** Admin only: suspend (false) or reactivate (true) a provider. Suspended providers receive no new orders. */
    @PostMapping("/set-active/{id}/{active}")
    public ResponseEntity<Response<Provider>> setActive(Authentication authentication, @PathVariable Long id, @PathVariable boolean active) {
        currentUserService.admin(authentication);
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No provider found with id: " + id));
        provider.setActive(active);
        return Response.response(providerRepository.save(provider), active ? "Provider activated" : "Provider suspended", ResponseType.SUCCESS);
    }

    /** The provider turns new orders on or off for themselves. Body: {"acceptingOrders": true}. */
    @PostMapping("/availability")
    public ResponseEntity<Response<Provider>> setAvailability(Authentication authentication, @RequestBody Map<String, Boolean> body) {
        Provider provider = currentUserService.provider(authentication);
        Boolean accepting = body == null ? null : body.get("acceptingOrders");
        if (accepting == null) {
            throw ApiException.badRequest("acceptingOrders is required");
        }
        provider.setAcceptingOrders(accepting);
        return Response.response(providerRepository.save(provider), "Availability updated", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- update

    /**
     * The provider (or an admin) can edit branches, services, links and offers.
     * Rate, followers count and the owning user can only be changed by an admin.
     */
    @Override
    @Transactional
    public ResponseEntity<Response<Provider>> update(Authentication authentication, @RequestBody ProviderDto dto) {
        User caller = currentUserService.user(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("provider id is required");
        }
        Provider provider = providerRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no provider with this id : " + dto.getId()));
        currentUserService.requireProviderOwnerOrAdmin(caller, provider);
        boolean admin = currentUserService.isAdmin(caller);

        if (admin) {
            if (dto.getUserId() != null) {
                User owner = userRepository.findById(dto.getUserId())
                        .orElseThrow(() -> ApiException.notFound("there is no user with this id : " + dto.getUserId()));
                provider.setUser(owner);
            }
            if (dto.getRate() != null) provider.setRate(dto.getRate());
            if (dto.getFollowersCount() != null) provider.setFollowersCount(dto.getFollowersCount());
        }

        if (dto.getBranches() != null) {
            for (Branch b : dto.getBranches()) {
                Branch branch = ownedBranchOrNew(b.getId(), provider);
                if (branch.getId() == null && (b.getName() == null || b.getName().trim().isEmpty())) {
                    throw ApiException.badRequest("branch name is required");
                }
                if (b.getName() != null) branch.setName(b.getName());
                if (b.getAddress() != null) branch.setAddress(b.getAddress());
                branch.setProvider(provider);
                branch = branchRepository.save(branch);
                if (b.getWorkTimes() != null) {
                    for (WorkTime wt : b.getWorkTimes()) {
                        WorkTime workTime = wt.getId() != null
                                ? ownedWorkTimeOrNew(wt.getId(), provider)
                                : workTimeRepository.findByBranchIdAndDay(branch.getId(), wt.getDay()).orElseGet(WorkTime::new);
                        if (wt.getDay() != null) workTime.setDay(wt.getDay());
                        if (wt.getStartTime() != null) workTime.setStartTime(wt.getStartTime());
                        if (wt.getCloseTime() != null) workTime.setCloseTime(wt.getCloseTime());
                        workTime.setBranch(branch);
                        workTimeRepository.save(workTime);
                    }
                }
            }
        }

        if (dto.getServices() != null) {
            List<Long> serviceIds = dto.getServices().stream()
                    .map(ProviderDto.ServiceRef::getId)
                    .filter(Objects::nonNull)
                    .toList();
            replaceServices(provider, serviceIds);
        }

        if (dto.getLinks() != null) {
            for (Links l : dto.getLinks()) {
                Links link = l.getId() != null ? ownedLinkOrNew(l.getId(), provider) : new Links();
                if (l.getLink() != null) link.setLink(l.getLink());
                link.setProvider(provider);
                linksRepository.save(link);
            }
        }

        if (dto.getOffers() != null) {
            for (Offer o : dto.getOffers()) {
                Offer offer = o.getId() != null ? ownedOfferOrNew(o.getId(), provider) : new Offer();
                if (o.getTitle() != null) offer.setTitle(o.getTitle());
                if (o.getDescription() != null) offer.setDescription(o.getDescription());
                if (o.getEndDate() != null) offer.setEndDate(o.getEndDate());
                offer.setProvider(provider);
                offerRepository.save(offer);
            }
        }
        provider = providerRepository.save(provider);
        return Response.response(provider, "provider updated successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/update-services")
    @Transactional
    public ResponseEntity<Response<Provider>> storeServicesForProvider(Authentication authentication, @RequestBody ServicesProviderDto dto) {
        User caller = currentUserService.user(authentication);
        if (dto.getProviderId() == null) {
            throw ApiException.badRequest("providerId is required");
        }
        Provider provider = providerRepository.findById(dto.getProviderId())
                .orElseThrow(() -> ApiException.notFound("No provider found with id: " + dto.getProviderId()));
        currentUserService.requireProviderOwnerOrAdmin(caller, provider);
        replaceServices(provider, dto.getServiceIds() == null ? List.of() : dto.getServiceIds());
        provider = providerRepository.save(provider);
        return Response.response(provider, "Services updated successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/update-all/{id}")
    @Transactional
    public ResponseEntity<Response<Provider>> updateAll(Authentication authentication, @PathVariable Long id, @RequestBody UpdateProviderDto dto) {
        User caller = currentUserService.user(authentication);
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No provider found with id: " + id));
        currentUserService.requireProviderOwnerOrAdmin(caller, provider);

        User user = provider.getUser();
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null && !dto.getEmail().equals(user.getEmail())) {
            if (dto.getEmail().trim().isEmpty()) {
                throw ApiException.badRequest("email must not be blank");
            }
            if (userRepository.findByEmail(dto.getEmail().trim()).isPresent()) {
                throw ApiException.conflict("User already exists with this email = " + dto.getEmail());
            }
            user.setEmail(dto.getEmail().trim());
        }
        userRepository.save(user);

        if (dto.getServiceIds() != null) {
            replaceServices(provider, dto.getServiceIds());
        }

        if (dto.getBranches() != null) {
            for (UpdateProviderDto.Branch b : dto.getBranches()) {
                Branch branch = b.getId() > 0 ? ownedBranchOrNew((long) b.getId(), provider) : new Branch();
                if (b.getName() != null) branch.setName(b.getName());
                if (b.getAddress() != null) branch.setAddress(b.getAddress());
                branch.setProvider(provider);
                branch = branchRepository.save(branch);
                if (b.getWorkTimes() != null) {
                    for (UpdateProviderDto.WorkTime wt : b.getWorkTimes()) {
                        WorkTime workTime;
                        if (wt.getId() > 0) {
                            workTime = ownedWorkTimeOrNew((long) wt.getId(), provider);
                        } else {
                            workTime = workTimeRepository.findByBranchIdAndDay(branch.getId(), wt.getDay()).orElseGet(WorkTime::new);
                        }
                        workTime.setDay(wt.getDay());
                        workTime.setStartTime(wt.getStartTime());
                        workTime.setCloseTime(wt.getCloseTime());
                        workTime.setBranch(branch);
                        workTimeRepository.save(workTime);
                    }
                }
            }
        }

        if (dto.getLinks() != null) {
            for (UpdateProviderDto.Link l : dto.getLinks()) {
                Links link = l.getId() > 0 ? ownedLinkOrNew((long) l.getId(), provider) : new Links();
                link.setLink(l.getLink());
                link.setProvider(provider);
                linksRepository.save(link);
            }
        }

        if (dto.getOffers() != null) {
            for (UpdateProviderDto.Offer o : dto.getOffers()) {
                Offer offer = o.getId() > 0 ? ownedOfferOrNew((long) o.getId(), provider) : new Offer();
                offer.setTitle(o.getTitle());
                offer.setDescription(o.getDescription());
                if (o.getEndDate() != null) offer.setEndDate(LocalDate.parse(o.getEndDate()));
                offer.setProvider(provider);
                offerRepository.save(offer);
            }
        }
        provider = providerRepository.save(provider);
        return Response.response(provider, "Provider updated successfully", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- follow

    @PostMapping("/follow-provider/{id}")
    @Transactional
    public ResponseEntity<Response<Follower>> follow(Authentication authentication, @PathVariable Long id) {
        User user = currentUserService.user(authentication);
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("provider id not exist"));
        FollowerId followerId = new FollowerId(user.getId(), id);
        if (followersRepository.existsById(followerId)) {
            throw ApiException.conflict("you are already following this provider");
        }
        Follower follower = new Follower();
        follower.setId(followerId);
        follower.setProvider(provider);
        follower.setUser(user);
        follower = followersRepository.save(follower);
        provider.setFollowersCount(provider.getFollowersCount() + 1);
        providerRepository.save(provider);
        return Response.response(follower, "Done", ResponseType.SUCCESS);
    }

    @DeleteMapping("/unfollow-provider/{id}")
    @Transactional
    public ResponseEntity<Response<Boolean>> unfollow(Authentication authentication, @PathVariable Long id) {
        User user = currentUserService.user(authentication);
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("provider id not exist"));
        FollowerId followerId = new FollowerId(user.getId(), id);
        if (!followersRepository.existsById(followerId)) {
            throw ApiException.notFound("you are not following this provider");
        }
        followersRepository.deleteById(followerId);
        long count = provider.getFollowersCount();
        provider.setFollowersCount(count > 0 ? count - 1 : 0);
        providerRepository.save(provider);
        return Response.response(true, "unfollowed successfully", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- admin: create provider with account

    @PostMapping("/create-by-admin")
    @Transactional
    public ResponseEntity<Response<Provider>> createByAdmin(Authentication authentication, @RequestBody CreateProviderDto dto) {
        currentUserService.admin(authentication);
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw ApiException.badRequest("email is required");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }
        if (dto.getProviderName() == null || dto.getProviderName().trim().isEmpty()) {
            throw ApiException.badRequest("providerName is required");
        }
        if (userRepository.findByEmail(dto.getEmail().trim()).isPresent()) {
            throw ApiException.conflict("User already exists with this email = " + dto.getEmail());
        }
        City city = null;
        if (dto.getCityId() != null) {
            city = cityRepository.findById(dto.getCityId())
                    .orElseThrow(() -> ApiException.badRequest("this city id not exist with id = " + dto.getCityId()));
        }
        if (dto.getBranches() != null) {
            for (CreateProviderDto.Branch b : dto.getBranches()) {
                if (b.getName() == null || b.getName().trim().isEmpty()) {
                    throw ApiException.badRequest("branch name is required");
                }
            }
        }

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail().trim());
        user.setPhone(dto.getPhone());
        user.setCity(city);
        user.setImage("download.jpeg");
        user.setBackgroundImage("download.jpeg");
        user.setRole(Role.PROVIDER);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user = userRepository.save(user);

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setName(dto.getProviderName().trim());
        provider.setFollowersCount(0L);
        provider.setRate(dto.getRate() != null ? dto.getRate() : 0.0);
        provider = providerRepository.save(provider);

        if (dto.getBranches() != null) {
            for (CreateProviderDto.Branch b : dto.getBranches()) {
                Branch branch = new Branch();
                branch.setName(b.getName());
                branch.setAddress(b.getAddress());
                branch.setProvider(provider);
                branch = branchRepository.save(branch);
                if (b.getWorkTimes() != null) {
                    for (CreateProviderDto.WorkTime wt : b.getWorkTimes()) {
                        WorkTime workTime = new WorkTime();
                        workTime.setDay(wt.getDay());
                        workTime.setStartTime(wt.getStartTime());
                        workTime.setCloseTime(wt.getCloseTime());
                        workTime.setBranch(branch);
                        workTimeRepository.save(workTime);
                    }
                }
            }
        }

        if (dto.getServiceIds() != null && !dto.getServiceIds().isEmpty()) {
            replaceServices(provider, dto.getServiceIds());
        }

        if (dto.getLinks() != null) {
            for (CreateProviderDto.Link l : dto.getLinks()) {
                Links link = new Links();
                link.setLink(l.getLink());
                link.setProvider(provider);
                linksRepository.save(link);
            }
        }

        if (dto.getOffers() != null) {
            for (CreateProviderDto.Offer o : dto.getOffers()) {
                Offer offer = new Offer();
                offer.setTitle(o.getTitle());
                offer.setDescription(o.getDescription());
                if (o.getEndDate() != null) offer.setEndDate(LocalDate.parse(o.getEndDate()));
                offer.setProvider(provider);
                offerRepository.save(offer);
            }
        }
        provider = providerRepository.save(provider);
        return Response.response(provider, "provider created successfully", ResponseType.SUCCESS);
    }

    // ---------------------------------------------------------------- helpers

    /** Replaces the services a provider offers (Service owns the join table). */
    private void replaceServices(Provider provider, List<Long> serviceIds) {
        List<Long> distinct = serviceIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Service> newServices = serviceRepository.findAllById(distinct);
        if (newServices.size() != distinct.size()) {
            throw ApiException.badRequest("One or more service ids do not exist");
        }
        List<Service> oldServices = provider.getServices() == null ? List.of() : new ArrayList<>(provider.getServices());
        for (Service oldService : oldServices) {
            oldService.getProviders().remove(provider);
            serviceRepository.save(oldService);
        }
        for (Service newService : newServices) {
            newService.getProviders().add(provider);
            serviceRepository.save(newService);
        }
        provider.setServices(newServices);
    }

    private Branch ownedBranchOrNew(Long id, Provider provider) {
        if (id == null) return new Branch();
        Optional<Branch> existing = branchRepository.findById(id);
        if (existing.isEmpty()) return new Branch();
        if (existing.get().getProvider() == null || !existing.get().getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("Branch " + id + " does not belong to this provider");
        }
        return existing.get();
    }

    private WorkTime ownedWorkTimeOrNew(Long id, Provider provider) {
        Optional<WorkTime> existing = workTimeRepository.findById(id);
        if (existing.isEmpty()) return new WorkTime();
        Branch branch = existing.get().getBranch();
        if (branch == null || branch.getProvider() == null || !branch.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("Work time " + id + " does not belong to this provider");
        }
        return existing.get();
    }

    private Links ownedLinkOrNew(Long id, Provider provider) {
        Optional<Links> existing = linksRepository.findById(id);
        if (existing.isEmpty()) return new Links();
        if (existing.get().getProvider() == null || !existing.get().getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("Link " + id + " does not belong to this provider");
        }
        return existing.get();
    }

    private Offer ownedOfferOrNew(Long id, Provider provider) {
        Optional<Offer> existing = offerRepository.findById(id);
        if (existing.isEmpty()) return new Offer();
        if (existing.get().getProvider() == null || !existing.get().getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("Offer " + id + " does not belong to this provider");
        }
        return existing.get();
    }
}

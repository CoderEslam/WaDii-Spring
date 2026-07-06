package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ProviderRequestDto;
import com.doubleclick.wadii.entities.*;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ProviderRequestRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/provider-requests")
public class ProviderRequestController {

    private static final String UPLOAD_DIR = "uploads/";

    private final ProviderRequestRepository providerRequestRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;

    @PostMapping(value = "/request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response<ProviderRequest>> requestToBeProvider(@ModelAttribute ProviderRequestDto dto) {
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
        request.setAddress(dto.getAddress());
        request.setPhoneNumber(dto.getPhoneNumber());
        request.setUser(userOptional.get());

        if (dto.getServiceIds() != null && !dto.getServiceIds().isEmpty()) {
            List<Service> services = serviceRepository.findAllById(dto.getServiceIds());
            if (services.size() != dto.getServiceIds().size()) {
                return Response.response(null, "One or more service IDs are invalid", ResponseType.NOT_FOUND);
            }
            request.setServices(services);
        }

        if (dto.getLinks() != null && !dto.getLinks().isEmpty()) {
            request.setLinks(dto.getLinks());
        }

        try {
            String uploadDir = System.getProperty("user.dir") + "/" + UPLOAD_DIR;
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (dto.getFrontIdImage() != null && !dto.getFrontIdImage().isEmpty()) {
                String filename = saveFile(dto.getFrontIdImage(), uploadDir);
                request.setFrontIdImage(filename);
            }
            if (dto.getBackIdImage() != null && !dto.getBackIdImage().isEmpty()) {
                String filename = saveFile(dto.getBackIdImage(), uploadDir);
                request.setBackIdImage(filename);
            }
            if (dto.getTaxCardFront() != null && !dto.getTaxCardFront().isEmpty()) {
                String filename = saveFile(dto.getTaxCardFront(), uploadDir);
                request.setTaxCardFront(filename);
            }
            if (dto.getTaxCardBack() != null && !dto.getTaxCardBack().isEmpty()) {
                String filename = saveFile(dto.getTaxCardBack(), uploadDir);
                request.setTaxCardBack(filename);
            }
        } catch (IOException e) {
            return Response.response(null, "Failed to upload images: " + e.getMessage(), ResponseType.INTERNAL_SERVER_ERROR);
        }

        request = providerRequestRepository.save(request);
        return Response.response(request, "Request submitted successfully", ResponseType.SUCCESS);
    }

    @GetMapping("/show-all")
    public ResponseEntity<Response<List<ProviderRequest>>> getAllRequests() {
        return Response.response(providerRequestRepository.findAll(), "All provider requests", ResponseType.SUCCESS);
    }

    @PostMapping("/accept/{id}")
    public ResponseEntity<Response<Provider>> acceptRequest(Authentication authentication, @PathVariable Long id) {
        Optional<User> adminOptional = userRepository.findByEmail(authentication.getName());
        if (adminOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (adminOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
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

        return Response.response(provider, "Request accepted, provider created successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<Response<ProviderRequest>> rejectRequest(Authentication authentication, @PathVariable Long id) {
        Optional<User> adminOptional = userRepository.findByEmail(authentication.getName());
        if (adminOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (adminOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        Optional<ProviderRequest> requestOptional = providerRequestRepository.findById(id);
        if (requestOptional.isEmpty()) {
            return Response.response(null, "No request found with id: " + id, ResponseType.NOT_FOUND);
        }
        ProviderRequest providerRequest = requestOptional.get();
        providerRequestRepository.deleteById(id);

        return Response.response(providerRequest, "Request rejected successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/put-it-user/{userId}")
    public ResponseEntity<Response<User>> revertProviderToUser(@PathVariable Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return Response.response(null, "No user found with id: " + userId, ResponseType.NOT_FOUND);
        }
        User user = userOptional.get();
        if (user.getRole() != Role.PROVIDER) {
            return Response.response(null, "User is not a provider", ResponseType.ERROR);
        }
        Optional<Provider> providerOptional = providerRepository.findByUserId(userId);
        if (providerOptional.isPresent()) {
            userRepository.save(user);
//            providerRepository.delete(providerOptional.get());
        }
        user.setRole(Role.USER);
        userRepository.save(user);
        return Response.response(user, "Provider reverted to user successfully", ResponseType.SUCCESS);
    }

    @PostMapping("/put-it-provider/{userId}")
    public ResponseEntity<Response<User>> revertUserToProvider(@PathVariable Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return Response.response(null, "No user found with id: " + userId, ResponseType.NOT_FOUND);
        }
        User user = userOptional.get();
        if (user.getRole() != Role.USER) {
            return Response.response(null, "User is not a User", ResponseType.ERROR);
        }
        Optional<Provider> providerOptional = providerRepository.findByUserId(userId);
        if (providerOptional.isPresent()) {
            user.setProvider(providerOptional.get());
            user.setRole(Role.PROVIDER);
            userRepository.save(user);
            return Response.response(user, "Provider reverted to user successfully", ResponseType.SUCCESS);
        }
        return Response.response(user, "you can't put it provider because there is no id of provider binding with it", ResponseType.SUCCESS);
    }

    private String saveFile(MultipartFile file, String uploadDir) throws IOException {
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        File dest = new File(uploadDir + File.separator + filename);
        file.transferTo(dest);
        return filename;
    }
}
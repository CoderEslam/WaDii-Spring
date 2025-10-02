package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ServicesProviderDto;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Service;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/providers")
public class ProviderController extends Controller<Provider, JsonObject, Long> {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;

    @Override
    public ResponseEntity<Response<Provider>> show(Long id) {
        return providerRepository.findById(id)
                .map(provider -> Response.response(provider, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no city with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Provider>> insert(Authentication authentication, JsonObject cityDto) {
//        if (cityDto.isNotEmpty()) {
//            Optional<Province> provinceOptional = provinceRepository.findById(cityDto.getProvinceId());
//            if (provinceOptional.isPresent()) {
//                City city = new City();
//                city.setName(cityDto.getName());
//                city.setProvince(provinceOptional.get());
//                city = cityRepository.save(city);
//                return Response.response(city, "City saved successfully", ErrorType.SUCCESS);
//            } else {
//                return Response.response(null, "there is no province with this id : " + cityDto.getProvinceId(), ErrorType.NOT_FOUND);
//            }
//        } else {
//            return Response.response(null, "name or province id is empty", ErrorType.ERROR);
//        }
        return null;
    }

    @Override
    public ResponseEntity<Response<Provider>> update(JsonObject cityDto) {
//        if (cityDto.isNotEmpty()) {
//            Optional<Province> provinceOptional = provinceRepository.findById(cityDto.getProvinceId());
//            if (provinceOptional.isPresent()) {
//                if (cityDto.getId() != null) {
//                    Optional<City> cityOptional = cityRepository.findById(cityDto.getId());
//                    if (cityOptional.isPresent()) {
//                        City city = cityOptional.get();
//                        city.setName(cityDto.getName());
//                        city.setProvince(provinceOptional.get());
//                        city = cityRepository.save(city);
//                        return Response.response(city, "City saved successfully", ErrorType.SUCCESS);
//                    } else {
//                        return Response.response(null, "there is no city with this id : " + cityDto.getId(), ErrorType.NOT_FOUND);
//                    }
//                } else {
//                    return Response.response(null, "city id not found", ErrorType.NOT_FOUND);
//                }
//            } else {
//                return Response.response(null, "there is no province with this id : " + cityDto.getProvinceId(), ErrorType.NOT_FOUND);
//            }
//        } else {
//            return Response.response(null, "name or province id is empty", ErrorType.ERROR);
//        }
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
}

package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ServicesDto;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.entities.Service;
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
@RequestMapping("/services")
public class ServiceController extends Controller<Service, ServicesDto, Long> {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;


    @Override
    public ResponseEntity<Response<Service>> show(Long id) {
        return serviceRepository.findById(id)
                .map(service -> Response.response(service, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no service with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Service>> insert(Authentication authentication, @RequestBody ServicesDto servicesDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (servicesDto.isNotEmpty()) {
            Optional<Service> serviceOptional = serviceRepository.findByName(servicesDto.getName());
            if (serviceOptional.isEmpty()) {
                Service service = new Service();
                service.setName(servicesDto.getName());
                service = serviceRepository.save(service);
                return Response.response(service, "Service saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is a service with this name : " + servicesDto.getName(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Service>> update(Authentication authentication, ServicesDto servicesDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (servicesDto.isNotEmpty()) {
            Optional<Service> serviceOptional = serviceRepository.findById(servicesDto.getId());
            if (serviceOptional.isPresent()) {
                Service service = serviceOptional.get();
                service.setId(servicesDto.getId());
                service.setName(servicesDto.getName());
                service = serviceRepository.save(service);
                return Response.response(service, "Service updated successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is a service with this name : " + servicesDto.getName(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(false, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(false, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        Optional<Service> serviceOptional = serviceRepository.findById(id);
        if (serviceOptional.isPresent()) {
            serviceRepository.deleteById(id);
            return Response.response(true, "service deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(false, "there is no service with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Service>>> readAll() {
        return Response.response(serviceRepository.findAll(), "All services", ResponseType.SUCCESS);
    }
}

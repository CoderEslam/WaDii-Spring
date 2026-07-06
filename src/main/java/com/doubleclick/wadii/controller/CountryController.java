package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.CountryDto;
import com.doubleclick.wadii.entities.Country;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.CountryRepository;
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
@RequestMapping("/countries")
public class CountryController extends Controller<Country, CountryDto, Long> {

    private final CountryRepository countryRepository;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Response<Country>> show(Long id) {
        return countryRepository.findById(id)
                .map(country -> Response.response(country, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No country with id: " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Country>> insert(Authentication authentication, @RequestBody CountryDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (dto.isNotEmpty()) {
            Optional<Country> existing = countryRepository.findByName(dto.getName());
            if (existing.isEmpty()) {
                Country country = new Country();
                country.setName(dto.getName());
                country = countryRepository.save(country);
                return Response.response(country, "Country saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "Country already exists: " + dto.getName(), ResponseType.ERROR);
            }
        } else {
            return Response.response(null, "Name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Country>> update(Authentication authentication, @RequestBody CountryDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (dto.isNotEmpty()) {
            Optional<Country> existing = countryRepository.findById(dto.getId());
            if (existing.isPresent()) {
                Country country = existing.get();
                country.setName(dto.getName());
                country = countryRepository.save(country);
                return Response.response(country, "Country updated successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "No country with id: " + dto.getId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "Name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Country>> delete(Authentication authentication, Long id) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (countryRepository.existsById(id)) {
            countryRepository.deleteById(id);
            return Response.response(null, "Country deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "No country with id: " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Country>>> readAll() {
        return Response.response(countryRepository.findAll(), "All countries", ResponseType.SUCCESS);
    }
}

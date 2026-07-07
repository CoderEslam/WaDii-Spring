package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.CityDto;
import com.doubleclick.wadii.entities.City;
import com.doubleclick.wadii.entities.Province;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.CityRepository;
import com.doubleclick.wadii.repository.ProvinceRepository;
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
@RequestMapping("/cities")
public class CityController extends Controller<City, CityDto, Long> {

    private final CityRepository cityRepository;
    private final ProvinceRepository provinceRepository;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Response<City>> show(Long id) {
        return cityRepository.findById(id)
                .map(city -> Response.response(city, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No city with id: " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<City>> insert(Authentication authentication, @RequestBody CityDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (dto.isNotEmpty()) {
            Optional<Province> province = provinceRepository.findById(dto.getProvinceId());
            if (province.isEmpty()) {
                return Response.response(null, "No province with id: " + dto.getProvinceId(), ResponseType.NOT_FOUND);
            }
            if (cityRepository.findByName(dto.getName()).isPresent()) {
                return Response.response(null, "City already exists: " + dto.getName(), ResponseType.ERROR);
            }
            City city = new City();
            city.setName(dto.getName());
            city.setProvince(province.get());
            city = cityRepository.save(city);
            return Response.response(city, "City saved successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "Name or provinceId is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<City>> update(Authentication authentication, @RequestBody CityDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (dto.isNotEmpty()) {
            Optional<City> existing = cityRepository.findById(dto.getId());
            if (existing.isEmpty()) {
                return Response.response(null, "No city with id: " + dto.getId(), ResponseType.NOT_FOUND);
            }
            Optional<Province> province = provinceRepository.findById(dto.getProvinceId());
            if (province.isEmpty()) {
                return Response.response(null, "No province with id: " + dto.getProvinceId(), ResponseType.NOT_FOUND);
            }
            City city = existing.get();
            city.setName(dto.getName());
            city.setProvince(province.get());
            city = cityRepository.save(city);
            return Response.response(city, "City updated successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "Name or provinceId is empty", ResponseType.ERROR);
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
        if (cityRepository.existsById(id)) {
            cityRepository.deleteById(id);
            return Response.response(true, "City deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(false, "No city with id: " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<City>>> readAll() {
        return Response.response(cityRepository.findAll(), "All cities", ResponseType.SUCCESS);
    }

    @GetMapping("/by-province/{provinceId}")
    public ResponseEntity<Response<List<City>>> byProvince(@PathVariable Long provinceId) {
        return Response.response(cityRepository.findByProvinceId_Id(provinceId), "Cities by province", ResponseType.SUCCESS);
    }
}

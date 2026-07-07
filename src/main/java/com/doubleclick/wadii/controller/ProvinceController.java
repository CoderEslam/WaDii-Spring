package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.ProvinceDto;
import com.doubleclick.wadii.entities.Country;
import com.doubleclick.wadii.entities.Province;
import com.doubleclick.wadii.repository.CountryRepository;
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
@RequestMapping("/provinces")
public class ProvinceController extends Controller<Province, ProvinceDto, Long> {

    private final ProvinceRepository provinceRepository;
    private final CountryRepository countryRepository;

    @Override
    public ResponseEntity<Response<Province>> show(Long id) {
        return provinceRepository.findById(id)
                .map(province -> Response.response(province, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "No province with id: " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Province>> insert(Authentication authentication, @RequestBody ProvinceDto dto) {
        if (dto.isNotEmpty()) {
            Optional<Country> country = countryRepository.findById(dto.getCountryId());
            if (country.isEmpty()) {
                return Response.response(null, "No country with id: " + dto.getCountryId(), ResponseType.NOT_FOUND);
            }
            if (provinceRepository.findByName(dto.getName()).isPresent()) {
                return Response.response(null, "Province already exists: " + dto.getName(), ResponseType.ERROR);
            }
            Province province = new Province();
            province.setName(dto.getName());
            province.setCountry(country.get());
            province = provinceRepository.save(province);
            return Response.response(province, "Province saved successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "Name or countryId is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Province>> update(Authentication authentication, @RequestBody ProvinceDto dto) {
        if (dto.isNotEmpty()) {
            Optional<Province> existing = provinceRepository.findById(dto.getId());
            if (existing.isEmpty()) {
                return Response.response(null, "No province with id: " + dto.getId(), ResponseType.NOT_FOUND);
            }
            Optional<Country> country = countryRepository.findById(dto.getCountryId());
            if (country.isEmpty()) {
                return Response.response(null, "No country with id: " + dto.getCountryId(), ResponseType.NOT_FOUND);
            }
            Province province = existing.get();
            province.setName(dto.getName());
            province.setCountry(country.get());
            province = provinceRepository.save(province);
            return Response.response(province, "Province updated successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "Name or country Id is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        if (provinceRepository.existsById(id)) {
            provinceRepository.deleteById(id);
            return Response.response(true, "Province deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(false, "No province with id: " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Province>>> readAll() {
        return Response.response(provinceRepository.findAll(), "All provinces", ResponseType.SUCCESS);
    }

    @GetMapping("/by-country/{countryId}")
    public ResponseEntity<Response<List<Province>>> byCountry(@PathVariable Long countryId) {
        return Response.response(provinceRepository.findByCountryId_Id(countryId), "Provinces by country", ResponseType.SUCCESS);
    }
}

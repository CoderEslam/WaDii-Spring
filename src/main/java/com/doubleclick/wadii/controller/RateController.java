package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.RateDto;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Rate;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.RateRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/rate")
public class RateController extends Controller<Rate, RateDto, Long> {

    private final RateRepository rateRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;


    @Override
    public ResponseEntity<Response<Rate>> show(Long id) {
        return rateRepository.findById(id)
                .map(carType -> Response.response(carType, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no rate with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Rate>> insert(Authentication authentication, RateDto rateDto) {
        if (rateDto.isNotEmpty()) {
            Optional<User> optionalUser = userRepository.findById(rateDto.getUserId());
            if (optionalUser.isPresent()) {
                Optional<Provider> providerOptional = providerRepository.findById(rateDto.getProviderId());
                if (providerOptional.isPresent()) {
                    Rate rate = new Rate();
                    rate.setRate(rateDto.getRate());
                    rate.setComment(rateDto.getComment());
                    rate.setProvider(providerOptional.get());
                    rate.setUser(optionalUser.get());
                    rate = rateRepository.save(rate);
                    return Response.response(rate, "Rate saved successfully", ResponseType.SUCCESS);
                } else {
                    //provider not exist
                    return Response.response(null, "Provider with this id = " + rateDto.getProviderId() + " not exist", ResponseType.SUCCESS);
                }
            } else {
                //user not exist
                return Response.response(null, "User with this id = " + rateDto.getUserId() + " not exist", ResponseType.SUCCESS);
            }
        } else {
            return Response.response(null, "some Feild not exist", ResponseType.SUCCESS);
        }
    }

    @Override
    public ResponseEntity<Response<Rate>> update(RateDto rateDto) {
        if (rateDto.isNotEmpty()) {
            Optional<Rate> rateOptional = rateRepository.findById(rateDto.getId());
            if (rateOptional.isPresent()) {
                Optional<User> optionalUser = userRepository.findById(rateDto.getUserId());
                if (optionalUser.isPresent()) {
                    Optional<Provider> providerOptional = providerRepository.findById(rateDto.getProviderId());
                    if (providerOptional.isPresent()) {
                        Rate rate = rateOptional.get();
                        rate.setRate(rate.getRate());
                        rate.setComment(rate.getComment());
                        rate.setProvider(rate.getProvider());
                        rate.setUser(rate.getUser());
                        rate = rateRepository.save(rate);
                        return Response.response(rate, "Rate saved successfully", ResponseType.SUCCESS);
                    } else {
                        //provider not exist
                        return Response.response(null, "Provider with this id = " + rateDto.getProviderId() + " not exist", ResponseType.SUCCESS);
                    }
                } else {
                    //user not exist
                    return Response.response(null, "User with this id = " + rateDto.getUserId() + " not exist", ResponseType.SUCCESS);
                }
            } else {
                return Response.response(null, "Rate with this id = " + rateDto.getId() + " not exist", ResponseType.SUCCESS);
            }
        } else {
            return Response.response(null, "some Feild not exist", ResponseType.SUCCESS);
        }
    }

    @Override
    public ResponseEntity<Response<Rate>> delete(Long id) {
        Optional<Rate> carType = rateRepository.findById(id);
        if (carType.isPresent()) {
            rateRepository.deleteById(id);
            return Response.response(null, "car deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no car with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Rate>>> readAll() {
        return Response.response(rateRepository.findAll(), "All rates", ResponseType.SUCCESS);
    }
}

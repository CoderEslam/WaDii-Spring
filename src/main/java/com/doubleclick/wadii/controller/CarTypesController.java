package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.CarTypesDto;
import com.doubleclick.wadii.entities.CarType;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.CarTypeRepository;
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
@RequestMapping("/car-types")
public class CarTypesController extends Controller<CarType, CarTypesDto, Long> {

    private final CarTypeRepository carTypeRepository;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Response<CarType>> show(Long id) {
        return carTypeRepository.findById(id)
                .map(carType -> Response.response(carType, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no car with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<CarType>> insert(Authentication authentication, @RequestBody CarTypesDto carTypesDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (carTypesDto.isNotEmpty()) {
            Optional<CarType> carType = carTypeRepository.findByName(carTypesDto.getName());
            if (carType.isEmpty()) {
                CarType car = new CarType();
                car.setName(carTypesDto.getName());
                car = carTypeRepository.save(car);
                return Response.response(car, "Car saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "this name is exist " + carTypesDto.getName(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<CarType>> update(Authentication authentication,CarTypesDto carTypesDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (carTypesDto.isNotEmpty()) {
            Optional<CarType> carType = carTypeRepository.findById(carTypesDto.getId());
            if (carType.isPresent()) {
                CarType car = carType.get();
                car.setId(car.getId());
                car.setName(carTypesDto.getName());
                car = carTypeRepository.save(car);
                return Response.response(car, "Car updated successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "this car is not exist with id : " + carTypesDto.getId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication,Long id) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(false, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(false, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        Optional<CarType> carType = carTypeRepository.findById(id);
        if (carType.isPresent()) {
            carTypeRepository.deleteById(id);
            return Response.response(true, "car deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(false, "there is no car with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<CarType>>> readAll() {
        return Response.response(carTypeRepository.findAll(), "All cars", ResponseType.SUCCESS);
    }
}

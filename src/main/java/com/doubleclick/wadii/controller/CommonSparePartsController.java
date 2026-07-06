package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.CommonSparePartsDto;
import com.doubleclick.wadii.entities.CommonSpareParts;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.repository.CommonSparePartsRepository;
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
@RequestMapping("/common-spare-parts")
public class CommonSparePartsController extends Controller<CommonSpareParts, CommonSparePartsDto, Long> {

    private final CommonSparePartsRepository commonSparePartsRepository;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Response<CommonSpareParts>> show(Long id) {
        return commonSparePartsRepository.findById(id)
                .map(part -> Response.response(part, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no spare part with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<CommonSpareParts>> insert(Authentication authentication, @RequestBody CommonSparePartsDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (dto.isNotEmpty()) {
            Optional<CommonSpareParts> existing = commonSparePartsRepository.findByName(dto.getName());
            if (existing.isEmpty()) {
                CommonSpareParts part = new CommonSpareParts();
                part.setName(dto.getName());
                part = commonSparePartsRepository.save(part);
                return Response.response(part, "Spare part saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is already a spare part with this name : " + dto.getName(), ResponseType.ERROR);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<CommonSpareParts>> update(Authentication authentication,@RequestBody CommonSparePartsDto dto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        if (dto.isNotEmpty()) {
            Optional<CommonSpareParts> partOptional = commonSparePartsRepository.findById(dto.getId());
            if (partOptional.isPresent()) {
                CommonSpareParts part = partOptional.get();
                part.setName(dto.getName());
                part = commonSparePartsRepository.save(part);
                return Response.response(part, "Spare part updated successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is no spare part with this id : " + dto.getId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<CommonSpareParts>> delete(Authentication authentication,Long id) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.ADMIN) {
            return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
        }
        Optional<CommonSpareParts> partOptional = commonSparePartsRepository.findById(id);
        if (partOptional.isPresent()) {
            commonSparePartsRepository.deleteById(id);
            return Response.response(null, "Spare part deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no spare part with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<CommonSpareParts>>> readAll() {
        return Response.response(commonSparePartsRepository.findAll(), "All spare parts", ResponseType.SUCCESS);
    }
}

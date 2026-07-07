package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.SparePartsDto;
import com.doubleclick.wadii.entities.SpareParts;
import com.doubleclick.wadii.repository.SparePartsRepository;
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
@RequestMapping("/spare-parts")
public class SparePartsController extends Controller<SpareParts, SparePartsDto, Long> {

    private final SparePartsRepository sparePartsRepository;

    @Override
    public ResponseEntity<Response<SpareParts>> show(Long id) {
        return sparePartsRepository.findById(id)
                .map(responses -> Response.response(responses, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no spare part with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<SpareParts>> insert(Authentication authentication, @RequestBody SparePartsDto sparePartsDto) {
        if (sparePartsDto.isNotEmpty()) {
            SpareParts spareParts = new SpareParts();
            spareParts.setSparePartName(sparePartsDto.getSparePartName());
            spareParts = sparePartsRepository.save(spareParts);
            return Response.response(spareParts, "Saved successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "name is empty", ResponseType.SUCCESS);
        }
    }

    @Override
    public ResponseEntity<Response<SpareParts>> update(Authentication authentication, SparePartsDto sparePartsDto) {
        Optional<SpareParts> sparePartsOptional = sparePartsRepository.findById(sparePartsDto.getId());
        if (sparePartsOptional.isPresent()) {
            if (sparePartsDto.isNotEmpty()) {
                SpareParts spareParts = sparePartsOptional.get();
                spareParts.setSparePartName(sparePartsDto.getSparePartName());
                spareParts = sparePartsRepository.save(spareParts);
                return Response.response(spareParts, "updated successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "name is empty", ResponseType.SUCCESS);
            }
        } else {
            return Response.response(null, "there is no spare part with this id : " + sparePartsDto.getId(), ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Optional<SpareParts> spareParts = sparePartsRepository.findById(id);
        if (spareParts.isPresent()) {
            sparePartsRepository.deleteById(id);
            return Response.response(true, "car deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(false, "there is no spare part with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<SpareParts>>> readAll() {
        return Response.response(sparePartsRepository.findAll(), "All spare parts", ResponseType.SUCCESS);
    }
}

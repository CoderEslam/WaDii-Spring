package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.ReasonDto;
import com.doubleclick.wadii.entities.Reason;
import com.doubleclick.wadii.repository.ReasonRepository;
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
@RequestMapping("/reasons")
public class ReasonController extends Controller<Reason, ReasonDto, Long> {

    private final ReasonRepository reasonRepository;

    @Override
    public ResponseEntity<Response<Reason>> show(Long id) {
        return reasonRepository.findById(id)
                .map(reason -> Response.response(reason, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no reason with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Reason>> insert(Authentication authentication, ReasonDto reasonDto) {
        if (reasonDto.getReason() == null || reasonDto.getReason().trim().isEmpty()) {
            return Response.response(null, "reason is required", ResponseType.ERROR);
        }
        Reason reason = new Reason();
        reason.setReason(reasonDto.getReason().trim());
        reason = reasonRepository.save(reason);
        return Response.response(reason, "Reason saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Reason>> update(Authentication authentication, ReasonDto reasonDto) {
        if (reasonDto.getId() == null) {
            return Response.response(null, "reason id is required", ResponseType.ERROR);
        }
        Optional<Reason> reasonOptional = reasonRepository.findById(reasonDto.getId());
        if (reasonOptional.isEmpty()) {
            return Response.response(null, "there is no reason with this id : " + reasonDto.getId(), ResponseType.NOT_FOUND);
        }
        Reason reason = reasonOptional.get();
        if (reasonDto.getReason() != null && !reasonDto.getReason().trim().isEmpty()) {
            reason.setReason(reasonDto.getReason().trim());
        }
        reason = reasonRepository.save(reason);
        return Response.response(reason, "Reason updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        if (!reasonRepository.existsById(id)) {
            return Response.response(false, "there is no reason with this id : " + id, ResponseType.NOT_FOUND);
        }
        reasonRepository.deleteById(id);
        return Response.response(true, "reason deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Reason>>> readAll() {
        return Response.response(reasonRepository.findAll(), "All reasons", ResponseType.SUCCESS);
    }
}

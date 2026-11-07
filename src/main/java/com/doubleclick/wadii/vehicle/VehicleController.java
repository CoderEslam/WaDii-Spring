package com.doubleclick.wadii.vehicle;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleService.VehicleView>>> list(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.list(me.id()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleService.VehicleView>> create(@AuthenticationPrincipal AuthUser me,
                                                                          @Valid @RequestBody VehicleService.VehicleInput body) {
        return ApiResponse.created(service.create(me.id(), body), "Vehicle saved");
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleService.VehicleView>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                          @Valid @RequestBody VehicleService.VehicleInput body) {
        return ApiResponse.ok(service.update(me.id(), id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> archive(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.archive(me.id(), id);
        return ApiResponse.ok(null);
    }
}

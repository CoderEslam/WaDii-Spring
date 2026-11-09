package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService service;
    private final DisputeRecorder recorder;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DisputeRecorder.View>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                                @RequestParam(required = false) Integer page,
                                                                                @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.mine(me, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> detail(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(service.detail(me, id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> open(@AuthenticationPrincipal AuthUser me,
                                                                  @Valid @RequestBody DisputeService.OpenInput body) {
        return ApiResponse.created(service.open(me, body), "Dispute opened");
    }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> evidence(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                      @Valid @RequestBody DisputeService.EvidenceInput body) {
        return ApiResponse.ok(recorder.addEvidence(me, id, body.note(), body.fileKey()));
    }
}

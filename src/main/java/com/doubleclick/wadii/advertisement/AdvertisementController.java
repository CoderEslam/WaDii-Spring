package com.doubleclick.wadii.advertisement;

import com.doubleclick.wadii.advertisement.AdvertisementService.AdRequest;
import com.doubleclick.wadii.advertisement.AdvertisementService.AdView;
import com.doubleclick.wadii.advertisement.AdvertisementService.DailyStat;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementService service;

    /** Public: ads currently live, by priority. */
    @GetMapping("/advertisements/live")
    public ResponseEntity<ApiResponse<PageResponse<AdView>>> live(@RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.live(page, size));
    }

    @PostMapping("/advertisements/{id}/impression")
    public ResponseEntity<ApiResponse<Void>> impression(@PathVariable Long id) {
        service.trackImpression(id);
        return ApiResponse.ok(null, "Recorded");
    }

    @PostMapping("/advertisements/{id}/click")
    public ResponseEntity<ApiResponse<Void>> click(@PathVariable Long id) {
        service.trackClick(id);
        return ApiResponse.ok(null, "Recorded");
    }

    @GetMapping("/admin/advertisements")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<AdView>>> list(@RequestParam(required = false) AdvertisementStatus status,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(status, page, size));
    }

    @PostMapping("/admin/advertisements")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<AdView>> create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AdRequest request) {
        return ApiResponse.created(service.create(me, request), "Advertisement created");
    }

    @PutMapping("/admin/advertisements/{id}")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<AdView>> update(@PathVariable Long id, @Valid @RequestBody AdRequest request) {
        return ApiResponse.ok(service.update(id, request), "Advertisement updated");
    }

    @PutMapping("/admin/advertisements/{id}/status/{status}")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<AdView>> status(@PathVariable Long id, @PathVariable AdvertisementStatus status) {
        return ApiResponse.ok(service.setStatus(id, status), "Status updated");
    }

    @DeleteMapping("/admin/advertisements/{id}")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Advertisement deleted");
    }

    @GetMapping("/admin/advertisements/{id}/stats")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<List<DailyStat>>> stats(@PathVariable Long id, @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(service.stats(id, days));
    }
}

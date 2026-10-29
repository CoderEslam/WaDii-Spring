package com.doubleclick.wadii.appointment;

import com.doubleclick.wadii.appointment.AppointmentService.AppointmentView;
import com.doubleclick.wadii.appointment.AppointmentService.NoShowRequest;
import com.doubleclick.wadii.appointment.AppointmentService.ScheduleRequest;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/appointment")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<AppointmentView>> get(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(appointmentService.get(me, orderId));
    }

    /** Schedules, or reschedules (reason required) the order appointment. */
    @PutMapping
    public ResponseEntity<ApiResponse<AppointmentView>> schedule(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                                 @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(appointmentService.schedule(me, orderId, request), "Appointment saved");
    }

    @PostMapping("/no-show")
    public ResponseEntity<ApiResponse<AppointmentView>> noShow(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                               @Valid @RequestBody(required = false) NoShowRequest request) {
        return ApiResponse.ok(appointmentService.reportNoShow(me, orderId, request), "No-show reported");
    }
}

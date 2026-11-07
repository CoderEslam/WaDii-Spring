package com.doubleclick.wadii.vehicle;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.util.Times;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository repository;
    private final Clock clock;

    public record VehicleInput(@NotBlank @Size(max = 60) String make, @NotBlank @Size(max = 60) String model,
                               @Min(1950) @Max(2100) int year) {
    }

    public record VehicleView(Long id, String make, String model, int year, String label, Instant lastUsedAt, Instant createdAt) {
        static VehicleView of(Vehicle v) {
            return new VehicleView(v.getId(), v.getMake(), v.getModel(), v.getYear(), v.label(), v.getLastUsedAt(), v.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<VehicleView> list(Long userId) {
        return repository.listForUser(userId).stream().map(VehicleView::of).toList();
    }

    @Transactional
    public VehicleView create(Long userId, VehicleInput input) {
        Vehicle v = new Vehicle();
        v.setUserId(userId);
        apply(v, input);
        return VehicleView.of(repository.save(v));
    }

    @Transactional
    public VehicleView update(Long userId, Long id, VehicleInput input) {
        Vehicle v = own(userId, id);
        apply(v, input);
        return VehicleView.of(v);
    }

    @Transactional
    public void archive(Long userId, Long id) {
        own(userId, id).setArchived(true);
    }

    @Transactional
    public Vehicle own(Long userId, Long id) {
        return repository.findByIdAndUserIdAndArchivedFalse(id, userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "Vehicle not found."));
    }

    @Transactional
    public void markUsed(Vehicle v) {
        v.setLastUsedAt(Instant.now(clock));
    }

    private void apply(Vehicle v, VehicleInput input) {
        int maxYear = Times.today(clock).getYear() + 1;
        if (input.year() > maxYear) {
            throw BusinessException.validation("year must be at most " + maxYear + ".");
        }
        v.setMake(input.make().trim());
        v.setModel(input.model().trim());
        v.setYear(input.year());
    }
}

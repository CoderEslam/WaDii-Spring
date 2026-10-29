package com.doubleclick.wadii.advertisement;

import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ads: ACTIVE / INACTIVE (manual, never touched by the job) / EXPIRED (job, after end_at). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private final AdvertisementRepository repository;
    private final WadiiProperties properties;
    private final Clock clock;

    public record AdRequest(@NotBlank @Size(max = 200) String title, @Size(max = 4000) String description,
                            @Size(max = 500) String imageUrl, @Size(max = 500) String targetUrl,
                            @Size(max = 150) String advertiserName, Integer priority, Instant startAt, Instant endAt) {
    }

    public record AdView(Long id, String title, String description, String imageUrl, String targetUrl, String advertiserName,
                         AdvertisementStatus status, int priority, long impressions, long clicks, Instant startAt, Instant endAt,
                         Instant createdAt) {
        static AdView of(Advertisement a) {
            return new AdView(a.getId(), a.getTitle(), a.getDescription(), a.getImageUrl(), a.getTargetUrl(), a.getAdvertiserName(),
                    a.getStatus(), a.getPriority(), a.getImpressions(), a.getClicks(), a.getStartAt(), a.getEndAt(), a.getCreatedAt());
        }
    }

    public record DailyStat(LocalDate day, long impressions, long clicks) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AdView> live(Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "priority").and(Sort.by("id")));
        return PageResponse.of(repository.findLive(clock.instant(), pageable), AdView::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdView> list(AdvertisementStatus status, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable), AdView::of);
    }

    @Transactional
    public AdView create(AuthUser me, AdRequest r) {
        Advertisement a = new Advertisement();
        a.setCreatedBy(me.id());
        apply(a, r);
        return AdView.of(repository.save(a));
    }

    @Transactional
    public AdView update(Long id, AdRequest r) {
        Advertisement a = load(id);
        apply(a, r);
        return AdView.of(a);
    }

    /** INACTIVE is a manual decision and stays until an admin changes it. ACTIVE on an ended ad is refused. */
    @Transactional
    public AdView setStatus(Long id, AdvertisementStatus status) {
        Advertisement a = load(id);
        if (status == AdvertisementStatus.ACTIVE && a.getEndAt() != null && !clock.instant().isBefore(a.getEndAt())) {
            throw BusinessException.of(ErrorCode.CONFLICT, "The ad already ended; change endAt first");
        }
        a.setStatus(status);
        return AdView.of(a);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(load(id));
    }

    @Transactional
    public void trackImpression(Long id) {
        track(id, true);
    }

    @Transactional
    public void trackClick(Long id) {
        track(id, false);
    }

    @Transactional(readOnly = true)
    public List<DailyStat> stats(Long id, int days) {
        load(id);
        LocalDate from = LocalDate.now(clock.withZone(properties.zone())).minusDays(Math.max(1, Math.min(days, 365)));
        return repository.dailyStats(id, from).stream()
                .map(r -> new DailyStat(((java.sql.Date) r[0]).toLocalDate(), ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
    }

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void expireEnded() {
        int expired = repository.expireEnded(clock.instant());
        if (expired > 0) log.info("{} advertisements expired", expired);
    }

    private void track(Long id, boolean impression) {
        Advertisement a = repository.findById(id).filter(x -> x.isLiveAt(clock.instant()))
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (impression) repository.incrementImpressions(a.getId());
        else repository.incrementClicks(a.getId());
        repository.upsertDaily(a.getId(), LocalDate.now(clock.withZone(properties.zone())), impression ? 1 : 0, impression ? 0 : 1);
    }

    private void apply(Advertisement a, AdRequest r) {
        if (r.startAt() != null && r.endAt() != null && !r.endAt().isAfter(r.startAt())) {
            throw BusinessException.validation("endAt must be after startAt");
        }
        a.setTitle(r.title().trim());
        a.setDescription(r.description());
        a.setImageUrl(r.imageUrl());
        a.setTargetUrl(r.targetUrl());
        a.setAdvertiserName(r.advertiserName());
        a.setPriority(r.priority() == null ? 0 : r.priority());
        a.setStartAt(r.startAt());
        a.setEndAt(r.endAt());
        if (a.getStatus() == AdvertisementStatus.EXPIRED && (r.endAt() == null || clock.instant().isBefore(r.endAt()))) {
            a.setStatus(AdvertisementStatus.ACTIVE);
        }
    }

    private Advertisement load(Long id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
    }
}

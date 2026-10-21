package com.doubleclick.wadii.service;

import com.doubleclick.wadii.entities.Advertisement;
import com.doubleclick.wadii.entities.AdvertisementStatus;
import com.doubleclick.wadii.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdvertisementScheduler {

    private final AdvertisementRepository advertisementRepository;

    /**
     * Runs every day at 00:01. Ads that are in their date range become ACTIVE,
     * the others become EXPIRED. INACTIVE ads were disabled by an admin and are left untouched.
     */
    @Scheduled(cron = "0 1 0 * * *")
    public void refreshAdvertisementStatuses() {
        LocalDate today = LocalDate.now();
        List<Advertisement> changed = new ArrayList<>();

        List<Advertisement> candidates = new ArrayList<>();
        candidates.addAll(advertisementRepository.findByStatus(AdvertisementStatus.ACTIVE));
        candidates.addAll(advertisementRepository.findByStatus(AdvertisementStatus.EXPIRED));

        for (Advertisement ad : candidates) {
            AdvertisementStatus expected = isInRange(ad, today) ? AdvertisementStatus.ACTIVE : AdvertisementStatus.EXPIRED;
            if (ad.getStatus() != expected) {
                ad.setStatus(expected);
                changed.add(ad);
            }
        }

        if (!changed.isEmpty()) {
            advertisementRepository.saveAll(changed);
        }
        log.info("Advertisement status refresh done, {} updated", changed.size());
    }

    private boolean isInRange(Advertisement ad, LocalDate today) {
        boolean afterStart = ad.getStartDate() == null || !today.isBefore(ad.getStartDate().toLocalDate());
        boolean beforeEnd = ad.getEndDate() == null || !today.isAfter(ad.getEndDate().toLocalDate());
        return afterStart && beforeEnd;
    }
}

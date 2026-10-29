package com.doubleclick.wadii.advertisement;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "advertisements")
@Getter
@Setter
@NoArgsConstructor
public class Advertisement extends AuditedEntity {

    @Column(nullable = false)
    private String title;

    private String description;
    private String imageUrl;
    private String targetUrl;
    private String advertiserName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdvertisementStatus status = AdvertisementStatus.ACTIVE;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private long impressions;

    @Column(nullable = false)
    private long clicks;

    private Instant startAt;
    private Instant endAt;

    @Column(name = "created_by")
    private Long createdBy;

    public boolean isLiveAt(Instant now) {
        return status == AdvertisementStatus.ACTIVE
                && (startAt == null || !now.isBefore(startAt))
                && (endAt == null || now.isBefore(endAt));
    }
}

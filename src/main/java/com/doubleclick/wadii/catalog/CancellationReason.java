package com.doubleclick.wadii.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cancellation_reasons")
@Getter
@Setter
@NoArgsConstructor
public class CancellationReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false)
    private ReasonAppliesTo appliesTo = ReasonAppliesTo.ANY;

    @Column(nullable = false)
    private boolean active = true;
}

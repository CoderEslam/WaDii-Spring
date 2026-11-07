package com.doubleclick.wadii.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** A service category a provider offers. Providers may quote only these categories. */
@Getter
@Entity
@Table(name = "provider_categories")
@IdClass(ProviderCategory.Key.class)
@NoArgsConstructor
public class ProviderCategory {

    @Id
    @Column(name = "provider_id")
    private Long providerId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    public ProviderCategory(Long providerId, Long categoryId) {
        this.providerId = providerId;
        this.categoryId = categoryId;
    }

    @lombok.EqualsAndHashCode
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long providerId;
        private Long categoryId;
    }
}

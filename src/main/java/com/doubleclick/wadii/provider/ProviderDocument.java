package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Private verification document; readable only by the applicant and staff. */
@Getter
@Setter
@Entity
@Table(name = "provider_documents")
@NoArgsConstructor
public class ProviderDocument extends BaseEntity {

    public enum Type { COMMERCIAL_REGISTER, TAX_CARD, NATIONAL_ID, SHOP_PHOTO, OTHER }

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private Type type;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;
}

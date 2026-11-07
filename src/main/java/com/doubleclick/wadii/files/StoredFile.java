package com.doubleclick.wadii.files;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata of an uploaded file. A file starts unattached (only its uploader can use it) and is attached to exactly
 * one record (dispute, order, application, ...); the parties of that record may then read it.
 */
@Getter
@Setter
@Entity
@Table(name = "stored_files")
@NoArgsConstructor
public class StoredFile extends BaseEntity {

    @Column(name = "file_key", nullable = false, updatable = false)
    private String fileKey;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FilePurpose purpose;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "public_file", nullable = false)
    private boolean publicFile;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;
}

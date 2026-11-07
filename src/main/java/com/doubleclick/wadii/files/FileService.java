package com.doubleclick.wadii.files;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Upload, attach and authorized download of files. */
@Service
@RequiredArgsConstructor
public class FileService {

    private static final int MAX_ATTACH = 10;

    private final FileStorageService storage;
    private final StoredFileRepository repository;
    /** Resolved lazily: modules that define rules also use this service, which would otherwise be a bean cycle. */
    private final ObjectProvider<FileAccessRule> rules;

    public record FileRef(String key, String contentType, long size, boolean isPublic) {
    }

    @Transactional
    public FileRef upload(AuthUser user, MultipartFile file, FilePurpose purpose) {
        FileStorageService.Stored stored = storage.store(file, purpose);
        StoredFile entity = new StoredFile();
        entity.setFileKey(stored.key());
        entity.setOwnerId(user.id());
        entity.setPurpose(purpose);
        entity.setContentType(stored.contentType());
        entity.setSizeBytes(stored.size());
        entity.setPublicFile(purpose.isPublic());
        repository.save(entity);
        return new FileRef(stored.key(), stored.contentType(), stored.size(), purpose.isPublic());
    }

    /**
     * Attaches files to a record. Every key must have been uploaded by {@code owner} with the expected purpose and
     * be unattached or already attached to this same record; otherwise the whole call fails.
     */
    @Transactional
    public List<StoredFile> attach(Long ownerId, List<String> keys, FilePurpose purpose, String refType, Long refId) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        if (keys.size() > MAX_ATTACH) {
            throw BusinessException.validation("At most " + MAX_ATTACH + " files can be attached.");
        }
        Map<String, StoredFile> byKey = repository.findByFileKeyIn(keys).stream()
                .collect(Collectors.toMap(StoredFile::getFileKey, Function.identity()));
        return keys.stream().distinct().map(key -> {
            StoredFile f = byKey.get(key);
            boolean sameRef = f != null && refType.equals(f.getRefType()) && refId.equals(f.getRefId());
            if (f == null || !f.getOwnerId().equals(ownerId) || f.getPurpose() != purpose
                    || (f.getRefType() != null && !sameRef)) {
                throw BusinessException.validation("Unknown or unusable file key: " + key);
            }
            f.setRefType(refType);
            f.setRefId(refId);
            return f;
        }).toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadPublic(String key) {
        StoredFile f = repository.findByFileKey(key).filter(StoredFile::isPublicFile)
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        return storage.serve(f.getFileKey(), f.getContentType(), true);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(AuthUser user, String key) {
        StoredFile f = repository.findByFileKey(key).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (!f.isPublicFile() && !canRead(user, f)) {
            throw BusinessException.forbidden();
        }
        return storage.serve(f.getFileKey(), f.getContentType(), f.isPublicFile());
    }

    private boolean canRead(AuthUser user, StoredFile f) {
        if (user.admin() || f.getOwnerId().equals(user.id())) {
            return true;
        }
        if (f.getRefType() == null) {
            return false;
        }
        return rules.orderedStream().filter(r -> r.supports(f.getRefType()))
                .anyMatch(r -> r.canRead(user, f.getRefType(), f.getRefId()));
    }
}

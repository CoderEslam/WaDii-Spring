package com.doubleclick.wadii.storage;

import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates and stores uploads. Checks size, extension, declared MIME type and file signature, all of which must
 * agree; the stored name is a random UUID. Public files live in {@code <root>/public}, private ones in
 * {@code <root>/private} and are only streamed by endpoints that checked access first.
 */
@Service
public class FileStorageService {

    private static final Pattern KEY = Pattern.compile("^[0-9a-f\\-]{36}\\.[a-z0-9]{2,5}$");

    private final Path publicDir;
    private final Path privateDir;

    public FileStorageService(WadiiProperties properties) {
        Path root = Path.of(properties.getStorage().getRoot()).toAbsolutePath().normalize();
        this.publicDir = root.resolve("public");
        this.privateDir = root.resolve("private");
    }

    public StoredFile store(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ErrorCode.FILE_REQUIRED);
        }
        if (file.getSize() > category.maxBytes()) {
            throw BusinessException.of(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();

        FileKind kind = category.allowed().stream().filter(k -> k.acceptsExtension(ext)).findFirst()
                .orElseThrow(() -> BusinessException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                        "Allowed types: " + category.allowed()));
        if (!kind.acceptsMime(mime)) {
            throw BusinessException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED, "The declared content type does not match the file extension");
        }
        try (InputStream in = file.getInputStream()) {
            if (!kind.matches(in.readNBytes(16))) {
                throw BusinessException.of(ErrorCode.FILE_CONTENT_MISMATCH);
            }
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "Failed to read the uploaded file");
        }
        String key = UUID.randomUUID() + "." + kind.extension();
        Path dir = category.isPrivate() ? privateDir : publicDir;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(key), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "Failed to save the uploaded file");
        }
        return new StoredFile(key, kind.primaryMime(), file.getSize());
    }

    public Resource loadPublic(String key) {
        return load(publicDir, key);
    }

    public Resource loadPrivate(String key) {
        return load(privateDir, key);
    }

    /** Streams a private file the caller was already authorized to read. */
    public ResponseEntity<Resource> servePrivate(String key, String contentType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(loadPrivate(key));
    }

    private Resource load(Path dir, String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw BusinessException.notFound(ErrorCode.NOT_FOUND);
        }
        Path path = dir.resolve(key).normalize();
        if (!path.getParent().equals(dir)) {
            throw BusinessException.notFound(ErrorCode.NOT_FOUND);
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw BusinessException.notFound(ErrorCode.NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw BusinessException.notFound(ErrorCode.NOT_FOUND);
        }
    }
}

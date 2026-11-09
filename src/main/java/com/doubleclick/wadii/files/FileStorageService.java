package com.doubleclick.wadii.files;

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
 * Validates and stores file bytes on disk. Size, extension, declared MIME type and file signature must all agree;
 * the stored name is a random UUID. Public files live in {@code <root>/public}, private ones in
 * {@code <root>/private} and are only streamed after {@link FileService} checked access.
 */
@Service
public class FileStorageService {

    private static final Pattern KEY = Pattern.compile("^[0-9a-f\\-]{36}\\.[a-z0-9]{2,5}$");

    private final Path publicDir;
    private final Path privateDir;

    public record Stored(String key, String contentType, long size) {
    }

    public FileStorageService(WadiiProperties properties) {
        Path root = Path.of(properties.getStorage().getRoot()).toAbsolutePath().normalize();
        this.publicDir = root.resolve("public");
        this.privateDir = root.resolve("private");
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public Stored store(MultipartFile file, FilePurpose purpose) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ErrorCode.FILE_REQUIRED);
        }
        if (file.getSize() > FilePurpose.MAX_BYTES) {
            throw BusinessException.of(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();

        FileKind kind = purpose.allowed().stream().filter(k -> k.acceptsExtension(ext)).findFirst()
                .orElseThrow(() -> BusinessException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED, "Allowed types: " + purpose.allowed()));
        if (!kind.acceptsMime(mime)) {
            throw BusinessException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED, "The declared content type does not match the file extension.");
        }
        try (InputStream in = file.getInputStream()) {
            if (!kind.matches(in.readNBytes(16))) {
                throw BusinessException.of(ErrorCode.FILE_CONTENT_MISMATCH);
            }
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "Failed to read the uploaded file.");
        }
        String key = UUID.randomUUID() + "." + kind.extension();
        Path dir = purpose.isPublic() ? publicDir : privateDir;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(key), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "Failed to save the uploaded file.");
        }
        return new Stored(key, kind.primaryMime(), file.getSize());
    }

    public ResponseEntity<Resource> serve(String key, String contentType, boolean publicFile) {
        Resource resource = load(publicFile ? publicDir : privateDir, key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                .header(HttpHeaders.CACHE_CONTROL, publicFile ? "public, max-age=86400" : "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    private Resource load(Path dir, String key) {
        if (!isValidKey(key)) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        Path path = dir.resolve(key).normalize();
        if (!path.getParent().equals(dir)) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw BusinessException.of(ErrorCode.NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
    }
}

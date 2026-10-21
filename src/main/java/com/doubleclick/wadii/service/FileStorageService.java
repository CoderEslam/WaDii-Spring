package com.doubleclick.wadii.service;

import com.doubleclick.wadii.exception.ApiException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded images under server-generated names.
 * Public images (avatars, backgrounds) live in uploads/, verification documents in uploads/private/
 * which is never served by the public image endpoint.
 */
@Service
public class FileStorageService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final Path publicDir = Paths.get("uploads").toAbsolutePath().normalize();
    private final Path privateDir = publicDir.resolve("private");

    public String storePublicImage(MultipartFile file) {
        return store(file, publicDir);
    }

    public String storePrivateImage(MultipartFile file) {
        return store(file, privateDir);
    }

    public Resource loadPublic(String filename) {
        return load(publicDir, filename);
    }

    public Resource loadPrivate(String filename) {
        return load(privateDir, filename);
    }

    private String store(MultipartFile file, Path dir) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file selected");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest("Only jpg, jpeg, png, gif and webp images are allowed");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw ApiException.badRequest("The uploaded file must be an image");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            if (!looksLikeImage(header)) {
                throw ApiException.badRequest("The uploaded file is not a valid image");
            }
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read the uploaded file");
        }
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save the uploaded file");
        }
        return filename;
    }

    private Resource load(Path dir, String filename) {
        try {
            Path path = dir.resolve(filename).normalize();
            if (!path.startsWith(dir) || !path.getParent().equals(dir)) {
                throw ApiException.badRequest("Invalid file name");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiException.notFound("File not found");
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw ApiException.badRequest("Invalid file name");
        }
    }

    private boolean looksLikeImage(byte[] h) {
        if (h.length < 4) return false;
        boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
        boolean webp = h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        return jpeg || png || gif || webp;
    }
}

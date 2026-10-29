package com.doubleclick.wadii.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Public images only (avatars, backgrounds). Private files have no route here. */
@RestController
@RequiredArgsConstructor
public class PublicFileController {

    private final FileStorageService storage;

    @GetMapping({"/api/v1/files/public/{key:.+}", "/users/{key:.+\\..+}"})
    public ResponseEntity<Resource> publicFile(@PathVariable String key) {
        Resource resource = storage.loadPublic(key);
        MediaType type = MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}

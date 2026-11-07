package com.doubleclick.wadii.files;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService service;

    @PostMapping
    public ResponseEntity<ApiResponse<FileService.FileRef>> upload(@AuthenticationPrincipal AuthUser me,
                                                                   @RequestPart("file") MultipartFile file,
                                                                   @RequestParam FilePurpose purpose) {
        return ApiResponse.created(service.upload(me, file, purpose), "Uploaded");
    }

    @GetMapping("/public/{key}")
    public ResponseEntity<Resource> publicFile(@PathVariable String key) {
        return service.downloadPublic(key);
    }

    @GetMapping("/{key}")
    public ResponseEntity<Resource> privateFile(@AuthenticationPrincipal AuthUser me, @PathVariable String key) {
        return service.download(me, key);
    }
}

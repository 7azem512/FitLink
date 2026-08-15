package com.project.FitLink.controller;

import com.project.FitLink.dto.GlobalResponse;
import com.project.FitLink.service.fileStorage.StorageService;
import com.project.FitLink.utils.enums.storage.StorageFolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * File upload endpoints.
 *
 * <p>Flow: the client uploads a file and receives its public URL back; that URL is then passed
 * in the JSON body of the related endpoint (e.g. a profile field inside {@code /auth/select-role})
 * and persisted on the entity. Both endpoints require an authenticated user.
 */
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
@Tag(name = "Storage")
public class StorageController {

    private final StorageService storageService;

    @Operation(summary = "Upload a single file",
            description = "Uploads one file to storage and returns its public URL. Use the returned URL as the value of the corresponding image/URL field when creating a profile (e.g. select-role).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @Parameter(description = "The file to upload", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Target folder that defines the allowed file types", required = true)
            @RequestParam("folder") StorageFolder folder) {
        String url = storageService.upload(file, folder);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("url", url);
        return ResponseEntity.ok(response.getApiResponse());
    }

    @Operation(summary = "Upload multiple files",
            description = "Uploads several files (e.g. a gym's additional images) and returns their public URLs in the same order.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/upload-many", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadMany(
            @Parameter(description = "The files to upload", required = true)
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "Target folder that defines the allowed file types", required = true)
            @RequestParam("folder") StorageFolder folder) {
        List<String> urls = storageService.uploadAll(files, folder);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("urls", urls);
        return ResponseEntity.ok(response.getApiResponse());
    }
}

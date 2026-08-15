package com.project.FitLink.service.fileStorage;

import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.utils.enums.storage.StorageFolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Storage service backed by Supabase's S3-compatible object storage.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>The client uploads a file via {@code StorageController} (multipart + a {@link StorageFolder}).</li>
 *   <li>The file is validated (not empty, size, allowed content type for the folder).</li>
 *   <li>The object is stored under {@code {bucket}/{folder.path}/{uuid}{ext}} and its
 *       <b>public URL</b> is returned.</li>
 *   <li>That URL is persisted on the related entity (e.g. a profile image field) when the
 *       profile is created or updated.</li>
 *   <li>The object can be removed later via {@link #delete(String)} (raw key) or
 *       {@link #deleteByUrl(String)} (the URL returned by {@link #upload(MultipartFile, StorageFolder)}).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private static final String PUBLIC_OBJECT_PATH = "/storage/v1/object/public/";

    private final S3Client s3Client;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.storage.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes;

    /**
     * Validates and uploads a single file to the given folder.
     *
     * @return the public URL of the stored object, ready to be persisted on an entity
     * @throws AppException VALIDATION_ERROR / FILE_TOO_LARGE / UNSUPPORTED_FILE_TYPE on invalid input,
     *                       STORAGE_UPLOAD_FAILED if the object could not be stored
     */
    public String upload(MultipartFile file, StorageFolder folder) {
        validateFile(file, folder);

        String key = buildKey(folder, file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
        } catch (IOException e) {
            log.error("Failed to read content of file for key={}", key, e);
            throw new AppException(ErrorCode.STORAGE_UPLOAD_FAILED, "Failed to read the uploaded file");
        } catch (SdkException e) {
            log.error("Failed to upload object key={}", key, e);
            throw new AppException(ErrorCode.STORAGE_UPLOAD_FAILED, "Failed to upload the file to storage");
        }

        String url = buildPublicUrl(key);
        log.info("Uploaded object key={}", key);
        return url;
    }

    /**
     * Uploads several files to the same folder (e.g. a gym's additional images).
     *
     * @return the public URLs in the same order as the input files
     */
    public List<String> uploadAll(List<MultipartFile> files, StorageFolder folder) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream().map(file -> upload(file, folder)).toList();
    }

    /**
     * Deletes an object by its raw storage key (e.g. {@code gym/logo/uuid.png}).
     */
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (SdkException e) {
            log.error("Failed to delete object key={}", key, e);
            throw new AppException(ErrorCode.STORAGE_DELETE_FAILED, "Failed to delete the file from storage");
        }
        log.info("Deleted object key={}", key);
    }

    /**
     * Deletes an object using the public URL returned by {@link #upload(MultipartFile, StorageFolder)}.
     * Safely no-ops on null/blank URLs.
     */
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        delete(extractKey(url));
    }

    private void validateFile(MultipartFile file, StorageFolder folder) {
        // Service-level guard: the servlet container already enforces the configured
        // multipart max size, this adds a clear, per-folder error for the client.
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "The uploaded file is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "The uploaded file exceeds the maximum allowed size");
        }
        if (!folder.accepts(file.getContentType())) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "File type '" + file.getContentType() + "' is not allowed for folder '" + folder.getPath() + "'");
        }
    }

    // Key = {folder path}/{random uuid}{original extension}. Only the extension is taken from the
    // client-provided filename, never the full name, to avoid unsafe characters.
    private String buildKey(StorageFolder folder, String originalFilename) {
        return folder.getPath() + "/" + UUID.randomUUID() + extractExtension(originalFilename);
    }

    private static String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot);
    }

    private String buildPublicUrl(String key) {
        return supabaseUrl + PUBLIC_OBJECT_PATH + bucket + "/" + key;
    }

    // Reverts a public URL back to its storage key so the same object can be deleted by URL.
    private String extractKey(String url) {
        String prefix = buildPublicUrl("");
        if (url != null && url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        return url;
    }
}

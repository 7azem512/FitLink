package com.project.FitLink.utils.enums.storage;

import lombok.Getter;

import java.util.Locale;
import java.util.Set;

/**
 * Defines the storage "folders" (S3 key prefixes) used to organize uploaded objects.
 *
 * <p>Each folder maps to a sub-path inside the bucket and declares the content types it accepts.
 * Objects are stored under {@code {folder.path}/{uuid}{ext}}, e.g.
 * {@code gym/logo/9f6c....png}. Grouping by feature keeps the bucket tidy and lets the client
 * pick the right folder for each upload (avatar, logo, cover, gallery, CV, intro video).
 */
@Getter
public enum StorageFolder {

    TRAINEE_AVATAR("trainee/avatar", "image/jpeg", "image/jpg", "image/png", "image/webp"),
    GYM_LOGO("gym/logo", "image/jpeg", "image/jpg", "image/png", "image/webp"),
    GYM_COVER("gym/cover", "image/jpeg", "image/jpg", "image/png", "image/webp"),
    GYM_GALLERY("gym/gallery", "image/jpeg", "image/jpg", "image/png", "image/webp"),
    COACH_CV("coach/cv", "application/pdf"),
    COACH_INTRO_VIDEO("coach/intro-video", "video/mp4", "video/quicktime", "video/x-msvideo");

    private final String path;
    private final Set<String> allowedContentTypes;

    StorageFolder(String path, String... allowedContentTypes) {
        this.path = path;
        this.allowedContentTypes = Set.of(allowedContentTypes);
    }

    /**
     * @return whether the given (lower-cased) content type is allowed for this folder
     */
    public boolean accepts(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase(Locale.ROOT));
    }
}

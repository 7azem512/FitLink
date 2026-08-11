package com.project.FitLink.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Wraps Google's official {@link GoogleIdTokenVerifier} to cryptographically
 * validate Google ID tokens (signature, expiration, issuer and audience)
 * before any identity claim is trusted.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final boolean requireVerifiedEmail;

    public GoogleTokenVerifier(
            @Value("${google.client-id}") String googleClientId,
            @Value("${google.require-verified-email:true}") boolean requireVerifiedEmail
    ) throws GeneralSecurityException, IOException {
        this.requireVerifiedEmail = requireVerifiedEmail;
        this.verifier = buildVerifier(googleClientId);
    }

    private static GoogleIdTokenVerifier buildVerifier(String googleClientId)
            throws GeneralSecurityException, IOException {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException(
                    "google.client-id is not configured. Set GOOGLE_CLIENT_ID."
            );
        }
        return new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(googleClientId))
                .setIssuer("https://accounts.google.com")
                .build();
    }

    /**
     * Verifies the raw Google ID token and returns its payload.
     * Throws {@link AppException} when the token is invalid, expired, has the
     * wrong audience/issuer, or the Google email is not verified (if enforced).
     */
    public GoogleIdToken.Payload verifyAndExtractPayload(String idToken) {
        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException e) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED, "Invalid Google ID token");
        }

        if (googleIdToken == null) {
            throw new AppException(
                    ErrorCode.GOOGLE_AUTH_FAILED,
                    "Invalid or expired Google ID token"
            );
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();

        if (requireVerifiedEmail && !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED, "Google email is not verified");
        }

        if (isBlank(payload.getSubject()) || isBlank(payload.getEmail())) {
            throw new AppException(
                    ErrorCode.GOOGLE_AUTH_FAILED,
                    "Google ID token is missing required identity data"
            );
        }

        return payload;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

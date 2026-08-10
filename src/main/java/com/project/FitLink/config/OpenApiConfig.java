package com.project.FitLink.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {

        return new OpenAPI()

                // Tell Swagger to use bearerAuth globally
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )

                // Define the bearer authentication scheme
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("""
                                                        Enter the access token only.
                                                        Swagger will automatically add:
                                                        Authorization: Bearer <access-token>
                                                        """)
                                )
                )

                .info(
                        new Info()
                                .title("FitLink Backend API")
                                .version("1.0.0")
                                .description("""
                                        ## Mobile Client Contract

                                        - Auth flow: register, verify the email OTP, select a role, then use access and refresh tokens.
                                        - Send protected requests with `Authorization: Bearer <access-token>`.
                                        - Access tokens are returned by login and email-OTP verification. Store tokens only in platform secure storage.
                                        - Refresh tokens are not rotated. Call `/auth/refresh-token` to obtain a new access token.
                                        - On `INVALID_REFRESH_TOKEN`, clear local credentials and return to login.
                                        - Handled API failures use `ErrorResponse`.
                                        - Mobile code should branch on `code`, not on the display `message`.
                                        - Registration OTPs expire after 10 minutes.
                                        - Password-reset OTPs expire after 5 minutes.
                                        - Respect `OTP_RESEND_COOLDOWN` and do not retry before the cooldown ends.
                                        - Rate limiting applies to every endpoint.
                                        - A rate-limited request returns HTTP 429 and a `Retry-After` header.
                                        - `401` means missing, invalid, or expired authentication.
                                        - `403` means authentication succeeded but the requested operation is not allowed.
                                        """)

                                .contact(
                                        new Contact()
                                                .name("Azmex")
                                                .email("azmex.app@gmail.com")
                                )

                                .license(
                                        new License()
                                                .name("Apache 2.0")
                                                .url("https://www.apache.org/licenses/LICENSE-2.0")
                                )
                );
    }
}
package com.project.FitLink.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Web-layer formatters for request binding (multipart form-data via @ModelAttribute).
 * The default Spring LocalTime formatter is strict about the input shape, so a
 * tolerant one is registered: it trims surrounding whitespace and accepts HH:mm,
 * HH:mm:ss and ISO time values (e.g. "08:00", "08:00:00", "20:30:15.123").
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatterForFieldType(LocalTime.class, new FlexibleLocalTimeFormatter());
    }

    private static final class FlexibleLocalTimeFormatter implements Formatter<LocalTime> {

        @Override
        public LocalTime parse(String text, Locale locale) {
            if (text == null || text.isBlank()) {
                return null;
            }
            String trimmed = text.trim();
            try {
                // ISO_LOCAL_TIME accepts HH:mm and HH:mm:ss[.fraction].
                return LocalTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_TIME);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid time value '" + trimmed + "'. Expected HH:mm or HH:mm:ss.", e);
            }
        }

        @Override
        public String print(LocalTime value, Locale locale) {
            return value == null ? null : value.toString();
        }
    }
}

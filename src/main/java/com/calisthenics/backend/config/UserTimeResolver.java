package com.calisthenics.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class UserTimeResolver {

    private final AppProperties appProperties;
    private final Clock clock;

    @Autowired
    public UserTimeResolver(AppProperties appProperties) {
        this(appProperties, Clock.systemUTC());
    }

    public UserTimeResolver(AppProperties appProperties, Clock clock) {
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public ZoneId resolveZone(String requestedTimezone) {
        if (requestedTimezone != null && !requestedTimezone.isBlank()) {
            try {
                return ZoneId.of(requestedTimezone.trim());
            } catch (Exception ignored) {
                // Fall back to configured application timezone if header/param is malformed.
            }
        }
        String configured = appProperties.getTimezone();
        if (configured != null && !configured.isBlank()) {
            try {
                return ZoneId.of(configured.trim());
            } catch (Exception ignored) {
            }
        }
        return ZoneId.of("Asia/Ho_Chi_Minh");
    }

    public LocalDate today(String requestedTimezone) {
        return clock.instant().atZone(resolveZone(requestedTimezone)).toLocalDate();
    }

    public LocalDate today(LocalDate explicitLocalDate, String requestedTimezone) {
        return explicitLocalDate != null ? explicitLocalDate : today(requestedTimezone);
    }

    public ZonedDateTime now(String requestedTimezone) {
        return clock.instant().atZone(resolveZone(requestedTimezone));
    }
}

package com.calisthenics.backend.security.registration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class RegistrationRateLimiter {

    private static final int MAX_BUCKETS = 10_000;
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> requests = new HashMap<>();

    public RegistrationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void consume(String bucket, int limit, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        removeExpiredBuckets(cutoff);
        if (!requests.containsKey(bucket) && requests.size() >= MAX_BUCKETS) {
            throw new RegistrationException(
                    "RATE_LIMIT_EXCEEDED",
                    "Quá nhiều yêu cầu đăng ký. Vui lòng thử lại sau.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        ArrayDeque<Instant> timestamps = requests.computeIfAbsent(bucket, ignored -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
            timestamps.removeFirst();
        }

        if (timestamps.size() >= limit) {
            throw new RegistrationException(
                    "RATE_LIMIT_EXCEEDED",
                    "Quá nhiều yêu cầu đăng ký. Vui lòng thử lại sau.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        timestamps.addLast(now);
    }

    private void removeExpiredBuckets(Instant cutoff) {
        Iterator<Map.Entry<String, ArrayDeque<Instant>>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ArrayDeque<Instant>> entry = iterator.next();
            if (entry.getValue().isEmpty() || !entry.getValue().peekLast().isAfter(cutoff)) {
                iterator.remove();
            }
        }
    }
}


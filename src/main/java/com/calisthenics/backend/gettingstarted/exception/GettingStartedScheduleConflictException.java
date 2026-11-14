package com.calisthenics.backend.gettingstarted.exception;

import com.calisthenics.backend.gettingstarted.dto.GettingStartedScheduleConflictResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class GettingStartedScheduleConflictException extends RuntimeException {

    private final List<GettingStartedScheduleConflictResponse> conflicts;

    public GettingStartedScheduleConflictException(
            String message,
            List<GettingStartedScheduleConflictResponse> conflicts
    ) {
        super(message);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }
}

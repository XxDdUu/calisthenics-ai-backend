package com.calisthenics.backend.workout;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.workout.dto.TodayWorkoutResolutionResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/workout-plans")
@RequiredArgsConstructor
@Tag(name = "Today's Workout", description = "Resolve today's workout source and scheduled plan")
@SecurityRequirement(name = "******")
public class TodayWorkoutController {

    private final TodayWorkoutResolver resolver;

    @GetMapping("/today")
    public TodayWorkoutResolutionResponse resolve(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long activePlanId) {
        return resolver.resolve(currentUser.getId(), date, activePlanId);
    }
}

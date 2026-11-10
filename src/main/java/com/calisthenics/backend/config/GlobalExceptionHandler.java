package com.calisthenics.backend.config;

import lombok.extern.slf4j.Slf4j;
import com.calisthenics.backend.aiworkout.exception.AiWorkoutPlanValidationException;
import com.calisthenics.backend.chat.exception.ChatAccessDeniedException;
import com.calisthenics.backend.chat.exception.ChatConversationNotFoundException;
import com.calisthenics.backend.coachclient.exception.CoachClientConflictException;
import com.calisthenics.backend.coachclient.exception.CoachClientForbiddenException;
import com.calisthenics.backend.coachclient.exception.CoachClientRelationshipNotFoundException;
import com.calisthenics.backend.coachclient.exception.WorkoutPlanAssignmentConflictException;
import com.calisthenics.backend.coachclient.exception.WorkoutPlanAssignmentNotFoundException;
import com.calisthenics.backend.security.passwordreset.PasswordResetException;
import com.calisthenics.backend.security.registration.AddressField;
import com.calisthenics.backend.security.registration.EmailAddressException;
import com.calisthenics.backend.security.registration.RegistrationException;
import com.calisthenics.backend.workoutplan.exception.ExerciseCatalogEntryNotFoundException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanExerciseNotFoundException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanExerciseOrderConflictException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("details", errors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(EmailAddressException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAddressException(EmailAddressException ex) {
        Map<String, Object> body = new HashMap<>();
        if (ex.getField() == AddressField.FROM) {
            body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            body.put("error", "Internal Server Error");
            body.put("message", "Hệ thống email chưa được cấu hình chính xác. Vui lòng liên hệ quản trị viên.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AiWorkoutPlanValidationException.class)
    public ResponseEntity<Map<String, Object>> handleAiWorkoutPlanValidation(AiWorkoutPlanValidationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("error", HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("details", ex.getViolations());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({
            WorkoutPlanNotFoundException.class,
            WorkoutPlanExerciseNotFoundException.class,
            ExerciseCatalogEntryNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleWorkoutPlanResourceNotFound() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "The requested workout plan resource was not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(WorkoutPlanExerciseOrderConflictException.class)
    public ResponseEntity<Map<String, Object>> handleWorkoutPlanExerciseOrderConflict() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", "An exercise already uses that display order in this session");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({
            CoachClientRelationshipNotFoundException.class,
            WorkoutPlanAssignmentNotFoundException.class,
            ChatConversationNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleCoachClientResourceNotFound() {
        return error(HttpStatus.NOT_FOUND, "Not Found", "The requested relationship resource was not found");
    }

    @ExceptionHandler({
            CoachClientForbiddenException.class,
            ChatAccessDeniedException.class,
            AccessDeniedException.class
    })
    public ResponseEntity<Map<String, Object>> handleCoachClientForbidden() {
        return error(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action");
    }

    @ExceptionHandler({
            CoachClientConflictException.class,
            WorkoutPlanAssignmentConflictException.class
    })
    public ResponseEntity<Map<String, Object>> handleCoachClientConflict() {
        return error(HttpStatus.CONFLICT, "Conflict", "The requested action conflicts with current state");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockConflict() {
        return error(HttpStatus.CONFLICT, "Conflict", "Your workout data was updated elsewhere. Refresh and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String details = root != null && root.getMessage() != null ? root.getMessage() : "";
        String lower = details.toLowerCase();

        if (lower.contains("uk_workout_plans_one_active_per_user")) {
            return error(HttpStatus.CONFLICT, "Conflict",
                    "Another workout plan is already active. Refresh your plans or confirm replacing the active plan.");
        }
        if (lower.contains("uk_workout_plan_exercise_order")) {
            return error(HttpStatus.CONFLICT, "Conflict",
                    "An exercise already uses that display order in this session.");
        }
        if (lower.contains("uk_active_session_for_scheduled_day")) {
            return error(HttpStatus.CONFLICT, "Conflict",
                    "An in-progress workout session already exists for this day.");
        }
        if (lower.contains("duplicate key") || lower.contains("unique constraint") || lower.contains("23505")) {
            return error(HttpStatus.CONFLICT, "Conflict",
                    "This action conflicts with an existing record. Please refresh and retry.");
        }
        if (lower.contains("check constraint") || lower.contains("not-null") || lower.contains("null value")) {
            return error(HttpStatus.BAD_REQUEST, "Bad Request",
                    "Invalid workout data: sets, reps, and hold durations must be positive when provided.");
        }
        return error(HttpStatus.CONFLICT, "Conflict", "The requested action conflicts with current state");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Invalid request body");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        body.put("error", "Unsupported Media Type");
        body.put("message", "The request content type is not supported");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "The requested resource was not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return error(status, status.getReasonPhrase(), reason);
    }

    @ExceptionHandler(PasswordResetException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordResetException(PasswordResetException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<Map<String, Object>> handleRegistrationException(RegistrationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

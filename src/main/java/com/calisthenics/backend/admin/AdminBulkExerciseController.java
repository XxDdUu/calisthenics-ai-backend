package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationItemResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationJobResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationRequest;
import com.calisthenics.backend.adminai.service.AdminAuthorizationService;
import com.calisthenics.backend.adminai.service.BulkExerciseGenerationService;
import com.calisthenics.backend.adminai.type.BulkExerciseItemOutcome;
import com.calisthenics.backend.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/admin/exercises/bulk-generate", "/api/admin/exercises/bulk-generate"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Bulk Exercise Generation", description = "Asynchronous chunked AI bulk exercise generation & duplicate inspection endpoints (Admin only)")
public class AdminBulkExerciseController {

    private final BulkExerciseGenerationService bulkExerciseGenerationService;
    private final AdminAuthorizationService adminAuthorizationService;

    @PostMapping
    @Operation(summary = "Launch an asynchronous chunked bulk exercise generation job (Admin only)")
    public ResponseEntity<BulkExerciseGenerationJobResponse> startBulkGeneration(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Request-ID", required = false) String requestIdHeader,
            @Valid @RequestBody BulkExerciseGenerationRequest request
    ) {
        User admin = adminAuthorizationService.requireAuthenticatedAdmin();
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            String headerKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                    ? idempotencyKeyHeader.trim()
                    : (requestIdHeader != null && !requestIdHeader.isBlank() ? requestIdHeader.trim() : null);
            request.setIdempotencyKey(headerKey);
        }
        return ResponseEntity.accepted()
                .body(bulkExerciseGenerationService.launchAndAwaitIfFast(admin, request, 1500L));
    }

    @GetMapping
    @Operation(summary = "List bulk exercise generation jobs for current administrator (Admin only)")
    public ResponseEntity<AdminPageResponse<BulkExerciseGenerationJobResponse>> listBulkJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User admin = adminAuthorizationService.requireAuthenticatedAdmin();
        return ResponseEntity.ok(bulkExerciseGenerationService.listJobs(admin, page, size));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get bulk exercise generation job progress and summary (Admin only)")
    public ResponseEntity<BulkExerciseGenerationJobResponse> getBulkJob(@PathVariable Long jobId) {
        User admin = adminAuthorizationService.requireAuthenticatedAdmin();
        return ResponseEntity.ok(bulkExerciseGenerationService.getJob(admin, jobId));
    }

    @GetMapping("/{jobId}/items")
    @Operation(summary = "Paginate saved/duplicate/failed items for a bulk generation job (Admin only)")
    public ResponseEntity<AdminPageResponse<BulkExerciseGenerationItemResponse>> getBulkJobItems(
            @PathVariable Long jobId,
            @RequestParam(required = false) BulkExerciseItemOutcome outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        User admin = adminAuthorizationService.requireAuthenticatedAdmin();
        return ResponseEntity.ok(bulkExerciseGenerationService.getJobItems(admin, jobId, outcome, page, size));
    }

    @PostMapping("/{jobId}/resume")
    @Operation(summary = "Safely resume an interrupted, failed, or partial bulk generation job (Admin only)")
    public ResponseEntity<BulkExerciseGenerationJobResponse> resumeBulkJob(@PathVariable Long jobId) {
        User admin = adminAuthorizationService.requireAuthenticatedAdmin();
        return ResponseEntity.ok(bulkExerciseGenerationService.resumeJob(admin, jobId));
    }
}

package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.*;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.EntitlementGrantSource;
import com.calisthenics.backend.user.EntitlementStatus;
import com.calisthenics.backend.user.MembershipEntitlement;
import com.calisthenics.backend.user.MembershipEntitlementRepository;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.VideoAnalysisQuotaService;
import com.calisthenics.backend.workout.PersistentWorkoutSessionService;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> USER_SORT_FIELDS = Set.of("id", "email", "fullName", "createdAt", "role");

    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutPlanMapper workoutPlanMapper;
    private final PersistentWorkoutSessionService persistentWorkoutSessionService;
    private final com.calisthenics.backend.exercise.ExerciseRepository exerciseRepository;
    private final VideoAnalysisQuotaService videoAnalysisQuotaService;
    private final MembershipEntitlementRepository membershipEntitlementRepository;
    private final AdminProAuditLogRepository adminProAuditLogRepository;
    private final Clock clock;

    public AdminService(
            UserRepository userRepository,
            CoachProfileRepository coachProfileRepository,
            ClientProfileRepository clientProfileRepository,
            WorkoutPlanRepository workoutPlanRepository,
            WorkoutSessionRepository workoutSessionRepository,
            WorkoutPlanMapper workoutPlanMapper,
            PersistentWorkoutSessionService persistentWorkoutSessionService,
            com.calisthenics.backend.exercise.ExerciseRepository exerciseRepository
    ) {
        this(
                userRepository,
                coachProfileRepository,
                clientProfileRepository,
                workoutPlanRepository,
                workoutSessionRepository,
                workoutPlanMapper,
                persistentWorkoutSessionService,
                exerciseRepository,
                null,
                null,
                null,
                null
        );
    }

    public AdminService(
            UserRepository userRepository,
            CoachProfileRepository coachProfileRepository,
            ClientProfileRepository clientProfileRepository,
            WorkoutPlanRepository workoutPlanRepository,
            WorkoutSessionRepository workoutSessionRepository,
            WorkoutPlanMapper workoutPlanMapper,
            PersistentWorkoutSessionService persistentWorkoutSessionService,
            com.calisthenics.backend.exercise.ExerciseRepository exerciseRepository,
            VideoAnalysisQuotaService videoAnalysisQuotaService
    ) {
        this(
                userRepository,
                coachProfileRepository,
                clientProfileRepository,
                workoutPlanRepository,
                workoutSessionRepository,
                workoutPlanMapper,
                persistentWorkoutSessionService,
                exerciseRepository,
                videoAnalysisQuotaService,
                null,
                null,
                null
        );
    }

    @Autowired
    public AdminService(
            UserRepository userRepository,
            CoachProfileRepository coachProfileRepository,
            ClientProfileRepository clientProfileRepository,
            WorkoutPlanRepository workoutPlanRepository,
            WorkoutSessionRepository workoutSessionRepository,
            WorkoutPlanMapper workoutPlanMapper,
            PersistentWorkoutSessionService persistentWorkoutSessionService,
            com.calisthenics.backend.exercise.ExerciseRepository exerciseRepository,
            @Autowired(required = false) VideoAnalysisQuotaService videoAnalysisQuotaService,
            @Autowired(required = false) MembershipEntitlementRepository membershipEntitlementRepository,
            @Autowired(required = false) AdminProAuditLogRepository adminProAuditLogRepository,
            @Autowired(required = false) Clock clock
    ) {
        this.userRepository = userRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutPlanMapper = workoutPlanMapper;
        this.persistentWorkoutSessionService = persistentWorkoutSessionService;
        this.exerciseRepository = exerciseRepository;
        this.videoAnalysisQuotaService = videoAnalysisQuotaService;
        this.membershipEntitlementRepository = membershipEntitlementRepository;
        this.adminProAuditLogRepository = adminProAuditLogRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                userRepository.count(),
                clientProfileRepository.count(),
                coachProfileRepository.count(),
                userRepository.countByRole(Role.ROLE_ADMIN),
                workoutSessionRepository.count(),
                workoutPlanRepository.count(),
                exerciseRepository.count()
        );
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserResponse> getUsers(
            int page,
            int size,
            String search,
            Role role,
            String sortBy,
            String sortDirection
    ) {
        Sort sort = userSort(sortBy, sortDirection);
        Pageable pageable = pageRequest(page, size, sort);
        Specification<User> specification = userSpecification(search, role);
        Page<User> users = userRepository.findAll(specification, pageable);
        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Set<Long> coachIds = userIds.isEmpty()
                ? Set.of()
                : coachProfileRepository.findUserIdsWithProfiles(userIds);
        Set<Long> clientIds = userIds.isEmpty()
                ? Set.of()
                : clientProfileRepository.findUserIdsWithProfiles(userIds);
        Map<Long, MembershipEntitlement> adminGrantsByUserId = (userIds.isEmpty() || membershipEntitlementRepository == null)
                ? Map.of()
                : membershipEntitlementRepository.findByUserIdInAndGrantSource(userIds, EntitlementGrantSource.ADMIN_GRANT)
                .stream()
                .collect(Collectors.toMap(MembershipEntitlement::getUserId, Function.identity(), (a, b) -> a));

        return AdminPageResponse.from(users.map(user -> toAdminUser(
                user,
                coachIds.contains(user.getId()),
                clientIds.contains(user.getId()),
                adminGrantsByUserId.get(user.getId())
        )));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        MembershipEntitlement adminGrant = membershipEntitlementRepository != null
                ? membershipEntitlementRepository.findByUserIdAndGrantSource(userId, EntitlementGrantSource.ADMIN_GRANT).orElse(null)
                : null;
        return toAdminUser(
                user,
                coachProfileRepository.findUserIdsWithProfiles(List.of(userId)).contains(userId),
                clientProfileRepository.findUserIdsWithProfiles(List.of(userId)).contains(userId),
                adminGrant
        );
    }

    @Transactional(readOnly = true)
    public AdminUserRoleResponse getUserRole(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new AdminUserRoleResponse(user.getId(), user.getRole());
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, Role requestedRole, Long adminUserId) {
        List<User> administrators = userRepository.findByRoleForUpdate(Role.ROLE_ADMIN);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getRole() != requestedRole
                && user.getId().equals(adminUserId)
                && requestedRole != Role.ROLE_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrators cannot demote themselves");
        }
        if (user.getRole() == Role.ROLE_ADMIN
                && requestedRole != Role.ROLE_ADMIN
                && administrators.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be demoted");
        }

        user.setRole(requestedRole);
        User updated = userRepository.save(user);
        if (videoAnalysisQuotaService != null) {
            videoAnalysisQuotaService.syncQuotaOnRoleChange(updated.getId(), requestedRole, updated.getMembershipTier());
        }
        return getUser(updated.getId());
    }

    @Transactional
    public AdminUserResponse grantAdminPro(Long targetUserId, Long adminUserId, String reason) {
        verifyActingAdmin(adminUserId);
        if (membershipEntitlementRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Entitlement repository is not available");
        }

        User user = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        MembershipEntitlement existing = membershipEntitlementRepository
                .findByUserIdAndGrantSource(targetUserId, EntitlementGrantSource.ADMIN_GRANT)
                .orElse(null);

        String previousEffectiveTier = resolveEffectiveTierName(user, existing);
        String previousStatus = existing != null ? existing.getStatus().name() : "NONE";
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        String cleanReason = sanitizeReason(reason);

        MembershipEntitlement entitlement = existing != null ? existing : MembershipEntitlement.builder()
                .userId(targetUserId)
                .tier(MembershipTier.PRO)
                .grantSource(EntitlementGrantSource.ADMIN_GRANT)
                .build();

        entitlement.setTier(MembershipTier.PRO);
        entitlement.setGrantSource(EntitlementGrantSource.ADMIN_GRANT);
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setUnlimited(true);
        entitlement.setGrantedByAdminId(adminUserId);
        entitlement.setGrantedAt(now);
        entitlement.setRevokedByAdminId(null);
        entitlement.setRevokedAt(null);
        entitlement.setReason(cleanReason);

        MembershipEntitlement savedEntitlement = membershipEntitlementRepository.saveAndFlush(entitlement);

        if (videoAnalysisQuotaService != null) {
            videoAnalysisQuotaService.syncQuotaOnEntitlementChange(user);
        }

        String newEffectiveTier = resolveEffectiveTierName(user, savedEntitlement);
        recordProAuditLog(
                targetUserId,
                adminUserId,
                AdminProAuditAction.GRANT_PRO,
                previousEffectiveTier,
                newEffectiveTier,
                previousStatus,
                EntitlementStatus.ACTIVE.name(),
                cleanReason
        );

        return getUser(targetUserId);
    }

    @Transactional
    public AdminUserResponse revokeAdminPro(Long targetUserId, Long adminUserId, String reason) {
        verifyActingAdmin(adminUserId);
        if (membershipEntitlementRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Entitlement repository is not available");
        }

        User user = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        MembershipEntitlement existing = membershipEntitlementRepository
                .findByUserIdAndGrantSource(targetUserId, EntitlementGrantSource.ADMIN_GRANT)
                .orElse(null);

        if (existing == null || existing.getStatus() != EntitlementStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User does not have an active admin-granted PRO entitlement"
            );
        }

        String previousEffectiveTier = resolveEffectiveTierName(user, existing);
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        String cleanReason = sanitizeReason(reason);

        existing.setStatus(EntitlementStatus.REVOKED);
        existing.setUnlimited(false);
        existing.setRevokedByAdminId(adminUserId);
        existing.setRevokedAt(now);
        if (cleanReason != null) {
            existing.setReason(cleanReason);
        }

        MembershipEntitlement savedEntitlement = membershipEntitlementRepository.saveAndFlush(existing);

        if (videoAnalysisQuotaService != null) {
            videoAnalysisQuotaService.syncQuotaOnEntitlementChange(user);
        }

        String newEffectiveTier = resolveEffectiveTierName(user, savedEntitlement);
        recordProAuditLog(
                targetUserId,
                adminUserId,
                AdminProAuditAction.REVOKE_PRO,
                previousEffectiveTier,
                newEffectiveTier,
                EntitlementStatus.ACTIVE.name(),
                EntitlementStatus.REVOKED.name(),
                cleanReason
        );

        return getUser(targetUserId);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminProAuditLogResponse> getProAuditLogs(Long targetUserId, int page, int size) {
        if (adminProAuditLogRepository == null) {
            return new AdminPageResponse<>(List.of(), page, size, 0, 0, false);
        }
        Pageable pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminProAuditLog> logs = targetUserId != null
                ? adminProAuditLogRepository.findByTargetUserIdOrderByCreatedAtDesc(targetUserId, pageable)
                : adminProAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return AdminPageResponse.from(logs.map(logEntry -> new AdminProAuditLogResponse(
                logEntry.getId(),
                logEntry.getTargetUserId(),
                logEntry.getAdminUserId(),
                logEntry.getAction().name(),
                logEntry.getGrantSource().name(),
                logEntry.getPreviousEffectiveTier(),
                logEntry.getNewEffectiveTier(),
                logEntry.getPreviousStatus(),
                logEntry.getNewStatus(),
                logEntry.getReason(),
                logEntry.getCreatedAt()
        )));
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminWorkoutPlanSummaryResponse> getWorkoutPlans(int page, int size) {
        Pageable pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return AdminPageResponse.from(workoutPlanRepository.findAdminSummaries(pageable));
    }

    @Transactional(readOnly = true)
    public AdminWorkoutPlanDetailResponse getWorkoutPlan(Long planId) {
        var plan = workoutPlanRepository.findWithDetailsById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout plan not found"));
        return new AdminWorkoutPlanDetailResponse(
                plan.getUser().getId(),
                plan.getUser().getEmail(),
                plan.getUser().getFullName(),
                workoutPlanMapper.toResponse(plan)
        );
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminWorkoutSessionSummaryResponse> getWorkoutSessions(int page, int size) {
        Pageable pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return AdminPageResponse.from(workoutSessionRepository.findAdminSummaries(pageable));
    }

    @Transactional(readOnly = true)
    public AdminWorkoutSessionDetailResponse getWorkoutSession(Long sessionId) {
        var session = workoutSessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout session not found"));
        return new AdminWorkoutSessionDetailResponse(
                session.getUser().getId(),
                session.getUser().getEmail(),
                session.getUser().getFullName(),
                persistentWorkoutSessionService.mapForAdmin(session)
        );
    }

    private AdminUserResponse toAdminUser(
            User user,
            boolean hasCoachProfile,
            boolean hasClientProfile,
            MembershipEntitlement adminGrant
    ) {
        boolean isAdminRole = user.getRole() == Role.ROLE_ADMIN;
        boolean paidPro = user.getMembershipTier() == MembershipTier.PRO;
        boolean adminGrantedPro = adminGrant != null && adminGrant.getStatus() == EntitlementStatus.ACTIVE;

        String effectiveTier = isAdminRole
                ? MembershipTier.ADMIN.name()
                : (adminGrantedPro || paidPro ? MembershipTier.PRO.name() : MembershipTier.FREE.name());
        String membershipSource = isAdminRole
                ? "ADMIN Role"
                : (adminGrantedPro ? "Admin Grant" : (paidPro ? "Subscription" : "Default"));
        boolean unlimited = isAdminRole || adminGrantedPro;

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getGender(),
                hasCoachProfile,
                hasClientProfile,
                user.getCreatedAt(),
                effectiveTier,
                membershipSource,
                unlimited,
                paidPro,
                adminGrantedPro,
                adminGrant != null ? adminGrant.getStatus().name() : "NONE",
                adminGrant != null ? adminGrant.getGrantSource().name() : null,
                adminGrant != null ? adminGrant.getGrantedByAdminId() : null,
                adminGrant != null ? adminGrant.getGrantedAt() : null,
                adminGrant != null ? adminGrant.getRevokedByAdminId() : null,
                adminGrant != null ? adminGrant.getRevokedAt() : null,
                adminGrant != null ? adminGrant.getReason() : null
        );
    }

    private void verifyActingAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator authentication is required");
        }
        User actingAdmin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator account not found"));
        if (actingAdmin.getRole() != Role.ROLE_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator privileges are required");
        }
    }

    private String resolveEffectiveTierName(User user, MembershipEntitlement adminGrant) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return MembershipTier.ADMIN.name();
        }
        if (adminGrant != null && adminGrant.getStatus() == EntitlementStatus.ACTIVE) {
            return MembershipTier.PRO.name();
        }
        return user.getMembershipTier() != null ? user.getMembershipTier().name() : MembershipTier.FREE.name();
    }

    private void recordProAuditLog(
            Long targetUserId,
            Long adminUserId,
            AdminProAuditAction action,
            String previousEffectiveTier,
            String newEffectiveTier,
            String previousStatus,
            String newStatus,
            String reason
    ) {
        if (adminProAuditLogRepository == null) {
            return;
        }
        adminProAuditLogRepository.save(AdminProAuditLog.builder()
                .targetUserId(targetUserId)
                .adminUserId(adminUserId)
                .action(action)
                .grantSource(EntitlementGrantSource.ADMIN_GRANT)
                .previousEffectiveTier(previousEffectiveTier)
                .newEffectiveTier(newEffectiveTier)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .reason(reason)
                .build());
    }

    private String sanitizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Pageable pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
        return PageRequest.of(page, size, sort);
    }

    private Sort userSort(String sortBy, String sortDirection) {
        if (!USER_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported user sort field");
        }
        final Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be ASC or DESC");
        }
        return Sort.by(direction, sortBy);
    }

    private Specification<User> userSpecification(String search, Role role) {
        Specification<User> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (search != null && !search.isBlank()) {
            String trimmedSearch = search.trim();
            String escapedSearch = trimmedSearch.toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escapedSearch + "%";
            Long numericId = parsePositiveLong(trimmedSearch);
            specification = specification.and((root, query, criteriaBuilder) -> {
                var emailMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\');
                var nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), pattern, '\\');
                if (numericId != null) {
                    return criteriaBuilder.or(emailMatch, nameMatch, criteriaBuilder.equal(root.get("id"), numericId));
                }
                return criteriaBuilder.or(emailMatch, nameMatch);
            });
        }
        if (role != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("role"), role));
        }
        return specification;
    }

    private Long parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = raw.startsWith("#") ? raw.substring(1).trim() : raw.trim();
        if (candidate.isEmpty()) {
            return null;
        }
        try {
            long value = Long.parseLong(candidate);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

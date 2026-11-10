package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.*;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.PersistentWorkoutSessionService;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

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
        this.userRepository = userRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutPlanMapper = workoutPlanMapper;
        this.persistentWorkoutSessionService = persistentWorkoutSessionService;
        this.exerciseRepository = exerciseRepository;
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

        return AdminPageResponse.from(users.map(user -> toAdminUser(
                user,
                coachIds.contains(user.getId()),
                clientIds.contains(user.getId())
        )));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toAdminUser(
                user,
                coachProfileRepository.findUserIdsWithProfiles(List.of(userId)).contains(userId),
                clientProfileRepository.findUserIdsWithProfiles(List.of(userId)).contains(userId)
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
        return getUser(updated.getId());
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

    private AdminUserResponse toAdminUser(User user, boolean hasCoachProfile, boolean hasClientProfile) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getGender(),
                hasCoachProfile,
                hasClientProfile,
                user.getCreatedAt()
        );
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
            String escapedSearch = search.trim().toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escapedSearch + "%";
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), pattern, '\\')
                    ));
        }
        if (role != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("role"), role));
        }
        return specification;
    }
}

package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminBootstrapRequest;
import com.calisthenics.backend.admin.dto.AdminBootstrapResponse;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    private static final String BOOTSTRAP_SECRET = "test-only-bootstrap-secret";

    @Mock
    private UserRepository userRepository;

    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new AdminBootstrapService(userRepository, BOOTSTRAP_SECRET);
    }

    @Test
    void validSecretPromotesAnExistingUser() {
        User user = User.builder().id(12L).email("admin@example.com").role(Role.ROLE_USER).build();
        when(userRepository.findByEmailIgnoreCaseForUpdate("admin@example.com")).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ROLE_ADMIN)).thenReturn(0L);
        when(userRepository.save(user)).thenReturn(user);

        AdminBootstrapResponse response = service.bootstrap(
                BOOTSTRAP_SECRET,
                new AdminBootstrapRequest("admin@example.com")
        );

        assertEquals(Role.ROLE_ADMIN, user.getRole());
        assertEquals(12L, response.userId());
        assertEquals(Role.ROLE_ADMIN, response.role());
        verify(userRepository).lockAllForAdminBootstrap();
    }

    @Test
    void invalidSecretIsRejectedBeforeLookingUpAnEmail() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.bootstrap("incorrect-secret", new AdminBootstrapRequest("admin@example.com"))
        );

        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingSecretIsRejectedBeforeLookingUpAnEmail() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.bootstrap(null, new AdminBootstrapRequest("admin@example.com"))
        );

        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(userRepository);
    }

    @Test
    void unknownEmailReturnsNotFoundAfterSecretValidation() {
        when(userRepository.findByEmailIgnoreCaseForUpdate("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.bootstrap(BOOTSTRAP_SECRET, new AdminBootstrapRequest("missing@example.com"))
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(userRepository).lockAllForAdminBootstrap();
    }

    @Test
    void bootstrapCannotBeRepeatedAfterAnAdministratorExists() {
        User user = User.builder().id(12L).email("admin@example.com").role(Role.ROLE_USER).build();
        when(userRepository.findByEmailIgnoreCaseForUpdate("admin@example.com")).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ROLE_ADMIN)).thenReturn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.bootstrap(BOOTSTRAP_SECRET, new AdminBootstrapRequest("admin@example.com"))
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(Role.ROLE_USER, user.getRole());
    }
}

package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.*;
import com.calisthenics.backend.security.passwordreset.PasswordResetService;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and JWT token management")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final FirebaseAuthService firebaseAuthService;
    private final GoogleAuthService googleAuthService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset OTP")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(new ForgotPasswordResponse(passwordResetService.requestOtp(request.getEmail())));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify a password reset OTP")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        PasswordResetService.VerificationResult result =
                passwordResetService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(new VerifyOtpResponse(result.resetToken(), result.expiresIn()));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a reset authorization token")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getResetToken(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Mật khẩu đã được cập nhật. Vui lòng đăng nhập lại.");
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/register", "/register/"})
    @Operation(summary = "Register a new user account")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", HttpStatus.BAD_REQUEST.value());
            error.put("error", "Bad Request");
            error.put("message", "Error: Email is already registered!");
            return ResponseEntity.badRequest().body(error);
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .fullName(registerRequest.getFullName())
                .experienceLevel(registerRequest.getExperienceLevel())
                .role(Role.ROLE_USER)
                .build();

        User savedUser = userRepository.save(user);
        UserPrincipal userPrincipal = UserPrincipal.create(savedUser);

        String accessToken = tokenProvider.generateAccessTokenFromUserPrincipal(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole().name())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping({"/login", "/login/"})
    @Operation(summary = "Authenticate user and issue JWT token")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

            String accessToken = tokenProvider.generateAccessToken(authentication);
            String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .userId(userPrincipal.getId())
                    .email(userPrincipal.getEmail())
                    .fullName(userPrincipal.getFullName())
                    .role(userPrincipal.getAuthorities().iterator().next().getAuthority())
                    .build();

            return ResponseEntity.ok(authResponse);
        } catch (BadCredentialsException ex) {
            try {
                boolean emailExists = userRepository.existsByEmail(loginRequest.getEmail());
                return loginError(
                        emailExists ? "INVALID_PASSWORD" : "EMAIL_NOT_FOUND",
                        emailExists ? "Mật khẩu không chính xác." : "Email này chưa được đăng ký."
                );
            } catch (Exception lookupException) {
                return unexpectedLoginError(lookupException);
            }
        } catch (Exception ex) {
            return unexpectedLoginError(ex);
        }
    }

    private ResponseEntity<LoginErrorResponse> loginError(String code, String message) {
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginErrorResponse(HttpStatus.UNAUTHORIZED.value(), code, message));
    }

    private ResponseEntity<LoginErrorResponse> unexpectedLoginError(Exception ex) {
        SecurityContextHolder.clearContext();
        log.error("Unexpected error during login", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new LoginErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_SERVER_ERROR",
                        "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."
                ));
    }

    @PostMapping({"/refresh", "/refresh/"})
    @Operation(summary = "Refresh expired JWT access token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        String token = refreshRequest.getRefreshToken();
        JwtValidationResult result = tokenProvider.validateRefreshToken(token);

        if (result == JwtValidationResult.VALID) {
            Long userId = tokenProvider.getUserIdFromJWT(token);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("status", HttpStatus.UNAUTHORIZED.value());
                error.put("error", "Unauthorized");
                error.put("message", "User not found");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            UserPrincipal userPrincipal = UserPrincipal.create(user);
            String newAccessToken = tokenProvider.generateAccessTokenFromUserPrincipal(userPrincipal);
            String newRefreshToken = tokenProvider.generateRefreshToken(userPrincipal);

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole().name())
                    .build();

            return ResponseEntity.ok(authResponse);
        }

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpStatus.UNAUTHORIZED.value());
        error.put("error", "Unauthorized");
        error.put("message", result == JwtValidationResult.EXPIRED ? "Refresh token has expired" : "Invalid or expired refresh token");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @PostMapping({"/logout", "/logout/"})
    @Operation(summary = "Log out user and clear security context")
    public ResponseEntity<Map<String, Object>> logout() {
        SecurityContextHolder.clearContext();
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/firebase", "/firebase/"})
    @Operation(summary = "Authenticate or auto-provision user using Firebase ID Token")
    public ResponseEntity<AuthResponse> authenticateWithFirebase(@Valid @RequestBody FirebaseLoginRequest request) {
        AuthResponse response = firebaseAuthService.authenticateWithFirebase(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/google", "/google/"})
    @Operation(summary = "Authenticate or provision user using verified Google ID Token")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = googleAuthService.authenticateWithGoogle(request);
        return ResponseEntity.ok(response);
    }
}

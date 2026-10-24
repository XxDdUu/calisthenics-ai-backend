package com.calisthenics.backend.security;

import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String servletPath = request.getServletPath();
        return path.startsWith("/api/v1/auth") || servletPath.startsWith("/api/v1/auth")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = getJwtFromRequest(request);

            if (StringUtils.hasText(token)) {
                // 1. Try local JWT token validation first
                if (tokenProvider.validateToken(token)) {
                    Long userId = tokenProvider.getUserIdFromJWT(token);
                    UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                    setAuthentication(request, userDetails);
                } 
                // 2. Try Firebase ID token validation
                else {
                    FirebaseToken firebaseToken = firebaseTokenVerifier.verifyToken(token);
                    if (firebaseToken != null) {
                        User user = findOrCreateFirebaseUser(firebaseToken);
                        UserDetails userDetails = UserPrincipal.create(user);
                        setAuthentication(request, userDetails);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User findOrCreateFirebaseUser(FirebaseToken firebaseToken) {
        String uid = firebaseToken.getUid();
        String email = firebaseToken.getEmail();
        String name = firebaseToken.getName() != null ? firebaseToken.getName() : "Calisthenics User";
        String picture = firebaseToken.getPicture();

        Optional<User> userOpt = userRepository.findByFirebaseUid(uid);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        if (email != null && !email.isBlank()) {
            Optional<User> emailOpt = userRepository.findByEmail(email);
            if (emailOpt.isPresent()) {
                User user = emailOpt.get();
                user.setFirebaseUid(uid);
                return userRepository.save(user);
            }
        }

        User newUser = User.builder()
                .email(email != null ? email : uid + "@firebase.user")
                .firebaseUid(uid)
                .fullName(name)
                .avatarUrl(picture)
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build();

        return userRepository.save(newUser);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

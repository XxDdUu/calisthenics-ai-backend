package com.calisthenics.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        Object validationResult = request.getAttribute(JwtAuthenticationFilter.JWT_VALIDATION_RESULT_ATTRIBUTE);

        String message;
        if (JwtValidationResult.EXPIRED.equals(validationResult)) {
            message = "Token has expired";
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
            log.debug("Rejecting expired JWT request to {}", request.getRequestURI());
        } else {
            message = "Authentication is required";
            if (validationResult != null) {
                response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token is invalid\"");
                log.debug("Rejecting invalid JWT request to {}", request.getRequestURI());
            } else {
                response.setHeader("WWW-Authenticate", "Bearer");
                log.debug("Rejecting unauthenticated request to {}", request.getRequestURI());
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                String.format("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}", message));
    }
}

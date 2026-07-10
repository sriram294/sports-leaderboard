package com.org.playboard.config;

import com.org.playboard.common.ApiException;
import com.org.playboard.service.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <accessToken>} and, if valid, sets the
 * caller's user id as the authentication principal. Runs before the
 * dispatcher, so an invalid/expired token is not surfaced as our usual
 * {@code ApiException} -> ProblemDetail response — it's swallowed and the
 * request proceeds unauthenticated, letting Spring Security's configured
 * {@link org.springframework.security.web.authentication.HttpStatusEntryPoint}
 * (see {@link SecurityConfig}) return 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                UUID userId = jwtService.verifyAccessToken(header.substring(BEARER_PREFIX.length()));
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (ApiException ignored) {
                // leave unauthenticated; anyRequest().authenticated() returns 401
            }
        }
        filterChain.doFilter(request, response);
    }
}

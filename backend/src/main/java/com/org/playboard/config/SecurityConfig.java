package com.org.playboard.config;

import com.org.playboard.service.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Every endpoint except POST /auth/google and POST /auth/refresh requires
    // Authorization: Bearer <accessToken> — see api-contracts.md § Conventions.
    // (POST /auth/logout is NOT in that exclusion list — it still needs a
    // valid access token, on top of the refresh token in its body.)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No httpBasic/formLogin is configured, so Spring Security's default
                // entry point is Http403ForbiddenEntryPoint — an unauthenticated
                // request to a protected endpoint would return 403, not 401. That
                // breaks the Android client's refresh-on-401 flow (an expired access
                // token would never trigger a token refresh). Return 401 instead so
                // an expired/missing token is a proper authentication challenge.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/google", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/avatars/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

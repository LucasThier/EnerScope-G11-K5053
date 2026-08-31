package org.enerscope.config;

import jakarta.servlet.http.HttpServletResponse;
import org.enerscope.auth.filter.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthFilter authFilter) throws Exception {
        // CSRF disabled: stateless REST API with JWT in the Authorization header
        // (LocalStorage).
        // Cross-origin protection is provided by CORS with explicit origins.
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public auth endpoints. Registration is intentionally NOT here:
                        // accounts are created by admins/org owners, not by self-service.
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // OpenAPI / Swagger UI
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
<<<<<<< HEAD
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
=======
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // 401 when there is no (valid) token, 403 when the token lacks the role.
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
>>>>>>> master
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }

    // Keeps security failures in the same ApiResponse envelope the rest of the API uses.
    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"data\":null,\"timestamp\":\""
                        + Instant.now() + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOrigins(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("*"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}

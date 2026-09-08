package org.enerscope.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    public AuthFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        Optional<Session> session = sessionService.validate(token);

        session.ifPresent(s -> {
            // Expose the platform role as a Spring Security authority so method
            // security (e.g. hasRole('ADMIN')) and the filter chain can gate on it.
            var authority = new SimpleGrantedAuthority("ROLE_" + s.getUser().getPlatformRole().name());
            var auth = new UsernamePasswordAuthenticationToken(
                    s.getUser(),
                    null,
                    List.of(authority)
            );
            auth.setDetails(s);
            SecurityContextHolder.getContext().setAuthentication(auth);
        });

        chain.doFilter(request, response);
    }

    // Tokens live in LocalStorage; read only from the Authorization: Bearer header.
    private String resolveToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }
}

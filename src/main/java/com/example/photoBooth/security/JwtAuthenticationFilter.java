package com.example.photoBooth.security;

import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateAndParseClaims(token);
            String username = claims.getSubject();

            Optional<User> optionalUser = userRepository.findByUsername(username);

            if (optionalUser.isPresent()) {
                User user = optionalUser.get();

                // JWT "iat" only has second-level precision, but passwordChangedAt is
                // stored with sub-second precision. Truncate passwordChangedAt to
                // seconds before comparing, otherwise a token issued in the very same
                // second as a password change (e.g. register-then-immediately-login)
                // gets spuriously rejected as "stale" even though it's the newest token.
                boolean passwordChangedAfterTokenIssued = user.getPasswordChangedAt()
                        .truncatedTo(ChronoUnit.SECONDS)
                        .isAfter(claims.getIssuedAt().toInstant());

                if (!passwordChangedAfterTokenIssued) {
                    UserPrincipal principal = new UserPrincipal(user);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException e) {
            // Invalid, expired, or malformed token — leave the request unauthenticated.
            // Spring Security's authorization rules will reject it if the endpoint requires auth.
        }

        filterChain.doFilter(request, response);
    }
}
package com.nordia.rooms.jwt;

import java.io.IOException;
import java.util.List;

import com.nordia.rooms.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Not a @Component: a Filter bean would also be registered in the servlet container, outside the security chain.
// A missing, invalid or revoked token leaves the request unauthenticated; the entry point answers 401 where a token
// is required. The token is kept as the credentials so that logout knows which token to revoke.
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final JwtBlacklistService jwtBlacklistService;

    public JwtFilter(JwtUtils jwtUtils, JwtBlacklistService jwtBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = jwtUtils.extractJwtFromHeader(request);
        if (token != null && jwtUtils.validateJwtToken(token)
                && !jwtBlacklistService.isTokenBlacklisted(jwtUtils.extractId(token))) {
            String email = jwtUtils.extractEmail(token);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(email, token, List.of()));
        }

        filterChain.doFilter(request, response);
    }
}

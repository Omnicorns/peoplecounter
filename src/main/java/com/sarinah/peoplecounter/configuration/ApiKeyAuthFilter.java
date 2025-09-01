package com.sarinah.peoplecounter.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    private final String expectedApiKey;
    private final String[] publicEndpoints;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiKeyAuthFilter(String expectedApiKey, String... publicEndpoints) {
        this.expectedApiKey = expectedApiKey;
        this.publicEndpoints = publicEndpoints == null ? new String[0] : publicEndpoints;
    }

    /** Skip filter untuk endpoint publik */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : publicEndpoints) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (expectedApiKey != null && expectedApiKey.equals(apiKeyHeader)) {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "apiKeyUser", null, List.of(new SimpleGrantedAuthority("ROLE_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
            return;
        }

        // API key salah / tidak ada -> 401 JSON
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", "Missing or invalid API key");
        body.put("path", request.getRequestURI());
        mapper.writeValue(response.getWriter(), body);
    }
}

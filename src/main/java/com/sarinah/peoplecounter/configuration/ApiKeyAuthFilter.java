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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-KEY";
    private final String expectedApiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiKeyAuthFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // — Lewatkan semua path di /api/health tanpa cek API-KEY
        if (path.startsWith("/api/health/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // — Ambil header X-API-KEY
        String apiKey = request.getHeader(API_KEY_HEADER);

        // — Jika cocok, buat Authentication dan simpan ke SecurityContext
        if (expectedApiKey.equals(apiKey)) {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "apiKeyUser",                  // principal (nama user semu)
                    null,                          // credentials
                    List.of(new SimpleGrantedAuthority("ROLE_API"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);

        } else {
            // — Jika tidak ada atau salah, return 401 Unauthorized
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String,Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
            body.put("error", "Unauthorized");
            body.put("message", "Invalid API Key");
            body.put("path", request.getRequestURI());

            mapper.writeValue(response.getWriter(), body);
            // --- Selesai JSON error response ---
        }
    }


}

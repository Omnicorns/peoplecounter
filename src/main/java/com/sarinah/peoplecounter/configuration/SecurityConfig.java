package com.sarinah.peoplecounter.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${api.key}")
    private String apiKey;

    private static final String[] PUBLIC_ENDPOINTS = {
            // Health & web
            "/api/health/**",
            "/",
            "/catalogue",
            "/SarinahForSEAGames2025",

            // Middleware pages
            "/middleware/dashboard",
            "/middleware/login",
            "/middleware/logout",
            "/middleware/users/register",
            "/middleware/users/forgot",
            "/middleware/users/inactive",
            "/middleware/sop",
            "/middleware/catalog/login",
            "/middleware/catalog/dashboard",
            "/middleware/catalog/logout",
            "/middleware/sop-bo",

            // Web pages
            "/web/login",
            "/web/product",
            "/web/logout",
            "/web/product/history/clear",
            "/web/sop",
            "/web/home",
            "/browse/**",

            // Admin API yang memang kamu jadikan public
            "/api/admin/users/import-csv",
            "/api/admin/users/pdf",
            "/api/admin/users/pdf/**",
            "/api/admin/users/catalogs",
            "/api/admin/users/**",

            // PWA & static root
            "/manifest.webmanifest",
            "/sw.js",
            "/favicon.ico",

            // Images / files root
            "/promis.png",
            "/login.png",
            "/sarinah.png",
            "/file.pdf",
            "/logo.jpeg",
            "/Danantara_Indonesia.png",
            "/injourney.png",
            "/logo2.png",
            "/template.png",
            "/template1.png",
            "/template-clean.png",
            "/walker-sprite.png",
            "/car-sprite.png",
            "/sarinah-clean.png",
            "/t.png",

            // Icons
            "/icons/**",
            "/icons/promis-512.png",
            "/icons/promis-192.png",
            "/icons/promis-maskable-512.png",

            // Top spender
            "/topspender",
            "/topspender89",
            "/topspender910",
            "/topspender810",

            // Townhall / booth
            "/townhall",
            "/portal/**",
            "/booth/**",
            "/boothIT.pdf",

            // Quiz Booth 3
            "/quiz-booth3",
            "/quiz-booth3.html",
            "/api/leaderboard/**",
            "/api/quiz-booth-3/**",
            "/haverst.mpeg",
            "/SARINAH_WASTRA_5.mp4",

            // Swagger
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private static final String[] STATIC_ROOT_ENDPOINTS = {
            "/*.png",
            "/*.jpg",
            "/*.jpeg",
            "/*.svg",
            "/*.ico",
            "/*.webmanifest",
            "/*.json",
            "/*.js",
            "/*.css",
            "/*.pdf",
            "/static/**",
            "/public/**",
            "/resources/**",
            "/META-INF/resources/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(
                apiKey,
                PUBLIC_ENDPOINTS,
                STATIC_ROOT_ENDPOINTS
        );

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth

                        // Static resources bawaan Spring Boot
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                        // Static file root
                        .requestMatchers(STATIC_ROOT_ENDPOINTS).permitAll()

                        // Public endpoint aplikasi
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Selain itu wajib API key / authenticated
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Custom API Key Filter.
     *
     * Endpoint public akan di-skip total oleh shouldNotFilter().
     * Endpoint selain public wajib kirim header:
     *
     * X-API-KEY: isi_api_key_kamu
     */
    static class ApiKeyAuthFilter extends OncePerRequestFilter {

        private final String apiKey;
        private final List<RequestMatcher> skipMatchers;

        public ApiKeyAuthFilter(String apiKey, String[]... skipPatternGroups) {
            this.apiKey = apiKey;

            this.skipMatchers = Arrays.stream(skipPatternGroups)
                    .flatMap(Arrays::stream)
                    .distinct()
                    .map(AntPathRequestMatcher::new)
                    .map(matcher -> (RequestMatcher) matcher)
                    .toList();
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            // OPTIONS jangan kena API key
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            // Skip endpoint public & static
            return skipMatchers.stream()
                    .anyMatch(matcher -> matcher.matches(request));
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            String requestApiKey = request.getHeader("X-API-KEY");

            if (apiKey == null || apiKey.isBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "API key belum dikonfigurasi");
                return;
            }

            if (requestApiKey == null || !apiKey.trim().equals(requestApiKey.trim())) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing API key");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "api-key-user",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_API"))
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        }
    }
}
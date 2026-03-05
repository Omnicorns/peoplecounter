package com.sarinah.peoplecounter.configuration;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${api.key}")
    private String apiKey;

    // Semua endpoint publik (selain static yang sudah di-handle PathRequest)
    private static final String[] PUBLIC_ENDPOINTS = {
            // Health & web
            "/api/health/**",
            "/",
            "/catalogue",
            "/SarinahForSEAGames2025",

            // Web pages kamu
            "/middleware/dashboard",
            "/middleware/login",
            "/middleware/logout",
            "/middleware/users/register",
            "/middleware/users/forgot",
            "/middleware/users/inactive",
            "/middleware/sop",

            "/web/login",
            "/web/product",
            "/web/logout",
            "/web/product/history/clear",
            "/web/sop",
            "/middleware/sop-bo",
            "/web/home",
            "/browse/**",

            // API tertentu (kalau memang publik)
            "/api/admin/users/import-csv",
            "/api/admin/users/pdf",
            "/api/admin/users/pdf/**",

            // PWA files (root)
            "/manifest.webmanifest",
            "/sw.js",
            "/promis.png",// <- perbaiki dari /static/promis.png
            "/login.png",
            "/sarinah.png",
            "/file.pdf",
            "icons/promis-512.png",
            "icons/promis-192.png",
            "icons/promis-maskable-512.png",
            "/favicon.ico",
            "/topspender89",
            "/topspender910",
            "/logo.jpeg",
            "/Danantara_Indonesia.png",
            "/injourney.png",
            "/t.png",

            // Swagger (opsional)
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ApiKey filter-mu — kita minta dia skip public & static
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(apiKey, PUBLIC_ENDPOINTS);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // 1) Izinkan static resources yang umum (/static/**, /public/**, /resources/**, webjars)
                        .requestMatchers(org.springframework.boot.autoconfigure.security.servlet.PathRequest
                                .toStaticResources().atCommonLocations())
                        .permitAll()

                        // 2) Izinkan file statis di ROOT (kalau kamu taruh di /static, di-serve di /)
                        .requestMatchers(
                                "/*.png", "/*.jpg", "/*.jpeg", "/*.svg", "/*.ico",
                                "/*.webmanifest","/file.pdf", "/*.json", "/*.js","/icons/**", "/*.png", "/*.css"
                        ).permitAll()

                        // 3) Endpoint publik lain
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // 4) Preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()


                        // 5) Sisanya wajib auth
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> res.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
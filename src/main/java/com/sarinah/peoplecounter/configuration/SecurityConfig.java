package com.sarinah.peoplecounter.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
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
            "/sales",
            "/api/dashboard",
            "/css/**",
            "/js/**",

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
            "/eproc.jpeg",
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
            "/portal-sarinah",

            // Quiz Booth 3
            "/quiz-booth3",
            "/quiz-booth3.html",
            "/api/leaderboard/**",
            "/api/quiz-booth-3/**",
            "/haverst.mpeg",
            "/SARINAH_WASTRA_5.mp4",
            "/videos/**",
            "/",

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<ClientRegistrationRepository> ssoRegistrations) throws Exception {
        ClientRegistrationRepository sso = ssoRegistrations.getIfAvailable();
        org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).info(
                sso == null ? "SSO Keycloak TIDAK aktif: portal (/) terbuka tanpa login"
                            : "SSO Keycloak AKTIF: portal (/) wajib login lewat Keycloak");

        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(
                apiKey,
                PUBLIC_ENDPOINTS,
                STATIC_ROOT_ENDPOINTS,
                // alur login/logout SSO tidak memakai API key
                new String[]{"/oauth2/**", "/login/oauth2/**", "/sso/logout"}
        );

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> {
                    // SSO aktif: halaman portal wajib login Keycloak (harus sebelum daftar PUBLIC_ENDPOINTS)
                    if (sso != null) {
                        auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                        auth.requestMatchers("/").authenticated();
                    }
                    auth

                        // Static resources bawaan Spring Boot
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                        // Static file root
                        .requestMatchers(STATIC_ROOT_ENDPOINTS).permitAll()

                        // Public endpoint aplikasi
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Selain itu wajib API key / authenticated
                        .anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            // Portal dibuka di browser tanpa login -> arahkan ke halaman login Keycloak
                            if (sso != null && "/".equals(req.getServletPath())) {
                                res.sendRedirect(req.getContextPath() + "/oauth2/authorization/" + SsoConfig.REGISTRATION_ID);
                                return;
                            }
                            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        })
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        if (sso != null) {
            OidcClientInitiatedLogoutSuccessHandler keycloakLogout = new OidcClientInitiatedLogoutSuccessHandler(sso);
            keycloakLogout.setPostLogoutRedirectUri("{baseUrl}/");
            http.oauth2Login(oauth -> oauth
                            .clientRegistrationRepository(sso)
                            .userInfoEndpoint(u -> u.oidcUserService(SsoConfig.oidcUserService()))
                            .defaultSuccessUrl("/", false)
                            .failureHandler((req, res, ex) -> {
                                String code = ex instanceof OAuth2AuthenticationException oae
                                        ? oae.getError().getErrorCode() : ex.getClass().getSimpleName();
                                org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                                        .warn("Login SSO gagal: [{}] {}", code, ex.getMessage(), ex);
                                res.setContentType("text/plain;charset=UTF-8");
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.getWriter().write("Login SSO gagal (" + code + "). Coba lagi atau hubungi Team IT.");
                            }))
                    // Keluar dari portal sekaligus mengakhiri sesi Keycloak
                    .logout(logout -> logout
                            .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/sso/logout"))
                            .logoutSuccessHandler(keycloakLogout)
                            .invalidateHttpSession(true)
                            .deleteCookies("JSESSIONID"));
        }
        return http.build();
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

            PathPatternRequestMatcher.Builder matchers = PathPatternRequestMatcher.withDefaults();
            this.skipMatchers = Arrays.stream(skipPatternGroups)
                    .flatMap(Arrays::stream)
                    .distinct()
                    .<RequestMatcher>map(matchers::matcher)
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
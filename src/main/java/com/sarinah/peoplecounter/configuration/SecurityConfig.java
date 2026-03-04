package com.sarinah.peoplecounter.configuration;


import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Merged Security Configuration
 *
 * Strategi: 2 SecurityFilterChain terpisah supaya tidak bentrok.
 *
 *   Chain 1 (@Order 1) — /api/**
 *     → ApiKey authentication, stateless, CSRF disabled
 *
 *   Chain 2 (@Order 2) — sisanya (web + portal)
 *     → LDAP/AD form login, session-based, CSRF enabled
 *
 * Kenapa dipisah?
 *   - ApiKeyFilter jangan sampai intercept form login (POST /portal/login)
 *   - Form login jangan sampai redirect API call ke halaman login
 *   - Session policy beda: API stateless, Web session-based
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // =============================================
    // Values
    // =============================================

    @Value("${api.key}")
    private String apiKey;

    @Value("${ldap.domain}")
    private String ldapDomain;

    @Value("${ldap.url}")
    private String ldapUrl;

    @Value("${ldap.base-dn}")
    private String ldapBaseDn;

    @Value("${ldap.user-search-filter}")
    private String userSearchFilter;

    // =============================================
    // Public endpoints — tidak perlu auth sama sekali
    // =============================================

    /** API endpoints yang public (skip ApiKey) */
    private static final String[] PUBLIC_API_ENDPOINTS = {
            "/api/health/**",
            "/api/admin/users/import-csv",
            "/api/admin/users/pdf",
            "/api/admin/users/pdf/**"
    };

    /** Web/Portal endpoints yang public */
    private static final String[] PUBLIC_WEB_ENDPOINTS = {
            "/",
            "/catalogue",
            "/SarinahForSEAGames2025",

            // Middleware pages
            "/middleware/login",
            "/middleware/logout",
            "/middleware/users/register",
            "/middleware/users/forgot",
            "/middleware/users/inactive",
            "/middleware/sop",
            "/middleware/sop-bo",
            "/middleware/announcements",

            // Web pages
            "/web/login",
            "/web/product",
            "/web/logout",
            "/web/product/history/clear",
            "/web/sop",
            "/web/home",
            "/browse/**",

            // Portal login (public, supaya user bisa akses form)
            "/portal/login",

            // PWA files
            "/manifest.webmanifest",
            "/sw.js",
            "/promis.png",
            "/login.png",
            "/sarinah.png",
            "/file.pdf",
            "/icons/promis-512.png",
            "/icons/promis-192.png",
            "/icons/promis-maskable-512.png",
            "/favicon.ico",

            // Swagger
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    // =============================================
    // LDAP / Active Directory Provider
    // =============================================

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider adAuthProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider =
                new ActiveDirectoryLdapAuthenticationProvider(ldapDomain, ldapUrl, ldapBaseDn);

        provider.setSearchFilter(userSearchFilter);
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);

        log.info("LDAP Auth configured: domain={}, url={}", ldapDomain, ldapUrl);
        return provider;
    }

    // =============================================================
    // CHAIN 1: /api/** → ApiKey auth, stateless
    // =============================================================

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {

        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(apiKey, PUBLIC_API_ENDPOINTS);

        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_API_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid API Key"))
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // =============================================================
    // CHAIN 2: Web + Portal → LDAP/AD form login
    // =============================================================

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {

        // Register AD provider untuk chain ini
        AuthenticationManagerBuilder authBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.authenticationProvider(adAuthProvider());

        return http
                .cors(Customizer.withDefaults())

                // === CSRF ===
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                "/web/logout",
                                "/middleware/logout"
                        )
                )

                // === SESSION ===
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                        .expiredUrl("/portal/login?expired=true")
                )

                // === AUTHORIZATION ===
                .authorizeHttpRequests(auth -> auth
                        // 1) Static resources bawaan Spring
                        .requestMatchers(
                                org.springframework.boot.autoconfigure.security.servlet.PathRequest
                                        .toStaticResources().atCommonLocations()
                        ).permitAll()

                        // 2) Static files di root
                        .requestMatchers(
                                "/*.png", "/*.jpg", "/*.jpeg", "/*.svg", "/*.ico",
                                "/*.webmanifest", "/*.json", "/*.js", "/*.css",
                                "/file.pdf", "/icons/**"
                        ).permitAll()

                        // 3) Public web endpoints
                        .requestMatchers(PUBLIC_WEB_ENDPOINTS).permitAll()

                        // 4) Preflight CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 5) Portal → wajib login LDAP
                        .requestMatchers("/portal/**").authenticated()

                        // 6) Middleware dashboard → wajib login LDAP
                        .requestMatchers("/middleware/dashboard").authenticated()

                        // 7) Sisanya → authenticated
                        .anyRequest().authenticated()
                )

                // === FORM LOGIN (LDAP/AD) ===
                .formLogin(form -> form
                        .loginPage("/portal/login")
                        .loginProcessingUrl("/portal/login")
                        .defaultSuccessUrl("/portal", true)
                        .failureUrl("/portal/login?error=true")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .permitAll()
                )

                // === LOGOUT ===
                .logout(logout -> logout
                        .logoutUrl("/portal/logout")
                        .logoutSuccessUrl("/portal/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // === ERROR HANDLING ===
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendRedirect("/portal/login"))
                        .accessDeniedHandler((req, res, e) ->
                                res.sendRedirect("/portal/login?denied=true"))
                )

                // === SECURITY HEADERS ===
                .headers(headers -> headers
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; " +
                                                "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; " +
                                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                                "img-src 'self' data:"))
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .build();
    }
}

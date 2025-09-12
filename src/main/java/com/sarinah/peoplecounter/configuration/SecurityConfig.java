package com.sarinah.peoplecounter.configuration;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;


@Configuration
public class SecurityConfig {

    @Value("${api.key}")
    private String apiKey;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/health/**",
            "/middleware/dashboard",
            "/middleware/login",
            "/middleware/logout",
            "/middleware/users/register",
            "/middleware/users/forgot",
            "/middleware/users/inactive",
            "/web/login",
            "/web/product",
            "/web/logout",
            "/web/product/history/clear",
            "/api/admin/users/import-csv",


                      // <-- login/register dlsb
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"  // opsional
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(apiKey, PUBLIC_ENDPOINTS);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

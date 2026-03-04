package com.sarinah.peoplecounter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;

/**
 * Audit Service - Logs all authentication events
 * Penting untuk detect brute force, credential stuffing, dll.
 */
@Slf4j
@Service
public class AuthAuditService {

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String ip = extractIp(event.getAuthentication().getDetails());

        log.info("AUTH_SUCCESS | user={} | ip={}", username, ip);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String ip = extractIp(event.getAuthentication().getDetails());
        String reason = event.getException().getMessage();

        log.warn("AUTH_FAILURE | user={} | ip={} | reason={}", username, ip, reason);

        // TODO: Implement brute force detection
        // - Track failed attempts per IP
        // - Block IP after N failures
        // - Send alert to admin
    }

    private String extractIp(Object details) {
        if (details instanceof WebAuthenticationDetails webDetails) {
            return webDetails.getRemoteAddress();
        }
        return "unknown";
    }
}

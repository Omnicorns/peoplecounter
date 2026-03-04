package com.sarinah.peoplecounter.controller;


import com.sarinah.peoplecounter.configuration.PortalProperties;
import com.sarinah.peoplecounter.model.PortalApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PortalController {

    private final PortalProperties portalProperties;

    /**
     * Login page
     */
    @GetMapping("/portal/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "expired", required = false) String expired,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "Username atau password salah. Pastikan menggunakan akun Active Directory.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Anda berhasil logout.");
        }
        if (expired != null) {
            model.addAttribute("errorMessage", "Sesi Anda telah berakhir. Silakan login kembali.");
        }

        return "login";
    }

    /**
     * Portal - Application selection page
     * Shows apps based on user's AD group/roles
     */
    @GetMapping("/portal")
    public String portalPage(Authentication auth, Model model) {
        String username = auth.getName();
        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        log.info("User '{}' accessed portal. Roles: {}", username, userRoles);

        // Filter apps based on user roles
        List<PortalApp> accessibleApps = portalProperties.getApps().stream()
                .filter(app -> {
                    if (app.getRoles() == null || app.getRoles().isEmpty()) {
                        return true; // No role restriction
                    }
                    // User has ROLE_ADMIN or matching role
                    return userRoles.contains("ROLE_ADMIN")
                            || app.getRoles().stream().anyMatch(userRoles::contains);
                })
                .collect(Collectors.toList());

        model.addAttribute("username", extractDisplayName(username));
        model.addAttribute("apps", accessibleApps);
        model.addAttribute("allRoles", userRoles);

        return "portal";
    }

    /**
     * Redirect to target application
     * Logs access for audit trail
     */
    @GetMapping("/portal/launch/{appId}")
    public RedirectView launchApp(@PathVariable String appId, Authentication auth) {
        String username = auth.getName();

        PortalApp targetApp = portalProperties.getApps().stream()
                .filter(app -> app.getId().equals(appId))
                .findFirst()
                .orElse(null);

        if (targetApp == null) {
            log.warn("User '{}' tried to access unknown app: {}", username, appId);
            return new RedirectView("/portal?error=app-not-found");
        }

        log.info("AUDIT: User '{}' launching app '{}' -> {}", username, targetApp.getName(), targetApp.getUrl());

        return new RedirectView(targetApp.getUrl());
    }

    /**
     * Extract display name from AD username
     * e.g., "sarinah\john.doe" -> "John Doe"
     */
    private String extractDisplayName(String username) {
        if (username.contains("\\")) {
            username = username.substring(username.indexOf("\\") + 1);
        }
        if (username.contains("@")) {
            username = username.substring(0, username.indexOf("@"));
        }
        return username.replace(".", " ")
                .substring(0, 1).toUpperCase() + username.replace(".", " ").substring(1);
    }
}

package com.sarinah.peoplecounter.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {
    @GetMapping("/")
    public String halaman(@AuthenticationPrincipal OidcUser user, Model model) {
        // Terisi bila login lewat SSO Keycloak; null bila SSO tidak aktif
        if (user != null) {
            String name = user.getFullName() != null && !user.getFullName().isBlank()
                    ? user.getFullName() : user.getPreferredUsername();
            model.addAttribute("ssoName", name);
        }
        return "landing";
    }
}

package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.MiddlewareUser;
import com.sarinah.peoplecounter.repository.MiddlewareUserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthServicePlain {
    private final MiddlewareUserRepository repo;

    public AuthServicePlain(MiddlewareUserRepository repo) {
        this.repo = repo;
    }

    public MiddlewareUser authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) return null;
        return repo.findByUsername(username.toLowerCase())
                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .filter(u -> rawPassword.equals(u.getPassword()))
                .orElse(null);
    }

    public void ensureDefaultAdmin() {
        String admin = "promisadmin";
        if (!repo.existsByUsername(admin)) {
            MiddlewareUser u = new MiddlewareUser();
            u.setUsername(admin);
            u.setPassword("promis123"); // plaintext
            u.setStatus("ACTIVE");
            repo.save(u);
        }
    }
}

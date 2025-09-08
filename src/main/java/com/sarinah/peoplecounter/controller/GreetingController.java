package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.configuration.LoggingFilterConfig;
import com.sarinah.peoplecounter.dto.User;
import com.sarinah.peoplecounter.entity.MiddlewareUser;
import com.sarinah.peoplecounter.entity.UserStatus;
import com.sarinah.peoplecounter.repository.UserRepository;
import com.sarinah.peoplecounter.request.ForgotPasswordRequest;
import com.sarinah.peoplecounter.request.RegisterRequest;
import com.sarinah.peoplecounter.service.AuthServicePlain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/middleware")
public class GreetingController {
    private final AuthServicePlain authService;
    private final UserRepository userRepo;

    public GreetingController(AuthServicePlain authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepo = userRepository;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session,Model model) {
        if (session.getAttribute("AUTH_USER_ID") != null) {
            return "redirect:/middleware/dashboard";
        }
        if (!model.containsAttribute("error")) model.addAttribute("error", null);
        return "middleware-login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String usernameOrEmail,
                          @RequestParam String password,
                          Model model,
                          HttpSession session) {

        MiddlewareUser user = authService.authenticate(usernameOrEmail, password);
        if (user == null) {
            model.addAttribute("error", "Username atau password salah, atau user non-aktif.");
            return "middleware-login";
        }

        session.setAttribute("AUTH_USER_ID", user.getId());
        session.setAttribute("AUTH_USERNAME", user.getUsername());

        return "redirect:/middleware/dashboard";
    }

    @PostMapping("/logout")
    public String doLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/middleware/login";
    }


    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model, HttpServletRequest request) {
        String username = (String) session.getAttribute("AUTH_USERNAME");
        if (username == null) {
            // jika belum login, redirect ke login
            return "redirect:/middleware/login";
        }
        ServletContext ctx = request.getServletContext();

        String  lastScanUser  = (String)  ctx.getAttribute(LoggingFilterConfig.ATTR_LAST_SCAN_USER);
        String  lastScanValue = (String)  ctx.getAttribute(LoggingFilterConfig.ATTR_LAST_SCAN_VALUE);
        Instant lastScanAtRaw = (Instant) ctx.getAttribute(LoggingFilterConfig.ATTR_LAST_SCAN_AT);

        // Konversi Instant -> LocalDateTime supaya gampang diformat di thymeleaf
        LocalDateTime lastScanAt = (lastScanAtRaw == null)
                ? null
                : LocalDateTime.ofInstant(lastScanAtRaw, ZoneId.systemDefault());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scans =
                (List<Map<String, Object>>) ctx.getAttribute(LoggingFilterConfig.ATTR_SCAN_TODAY);
        if (scans == null) scans = List.of();

        model.addAttribute("username", username);
        model.addAttribute("fullName",username);

        List<User> list = userRepo.findAll().stream()
                .map(data -> com.sarinah.peoplecounter.dto.User.builder()
                        .username(data.getUsername())
                        .fullName(data.getFullName())
                        .status(String.valueOf(data.getStatus()))
                        .build())
                .collect(Collectors.toList());
        model.addAttribute("message", "Welcome to our platform!");
        model.addAttribute("users", list);
        model.addAttribute("username", username);

        model.addAttribute("lastScanUser",  lastScanUser);
        model.addAttribute("lastScanValue", lastScanValue);
        model.addAttribute("lastScanAt",    lastScanAt);
        model.addAttribute("scanToday",     scans);
        return "middleware-register";
    }


    // === POST /users/register: terima JSON dari modal Add User ===
    @PostMapping(path = "/users/register", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Object registerUser(@RequestBody RegisterRequest req, HttpSession session) {
        // (opsional) hanya boleh oleh user yang sudah login
        if (session.getAttribute("AUTH_USER_ID") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        // Validasi sesuai form JSON
        if (req.getUsername() == null || req.getUsername().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username wajib diisi");
        if (req.getFullName() == null || req.getFullName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full Name wajib diisi");
        if (req.getPassword() == null || req.getPasswordConfirm() == null
                || req.getPassword().isBlank() || req.getPasswordConfirm().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password & konfirmasi wajib diisi");
        if (!req.getPassword().equals(req.getPasswordConfirm()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password & konfirmasi tidak sama");
        if (userRepo.existsByUsername(req.getUsername().toLowerCase()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username sudah dipakai");
        if (!req.isAcceptTerms())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Harus menyetujui Ketentuan Layanan");

        // ==== SIMPAN KE TABEL USERS PROMIS ====
        // Catatan: PROMIS kamu sebelumnya memakai kolom passwordHash (BCrypt).
        // Jika memang mau tetap BCrypt di PROMIS (lebih aman), gunakan baris di bawah.
        String hash = org.springframework.security.crypto.bcrypt.BCrypt
                .hashpw(req.getPassword(), org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10));

        var user = com.sarinah.peoplecounter.entity.User.builder()
                .fullName(req.getFullName().trim())
                .username(req.getUsername().toLowerCase().trim())
                // .email(...)  // sengaja tidak dipakai karena form tidak kirim email
                // .phone(...)  // idem
                .passwordHash(hash)                 // <-- kolom PROMIS
                .termsAcceptedAt(Instant.now())
                .status(UserStatus.ACTIVE)          // jika bukan enum: pakai string "ACTIVE"
                .build();

        userRepo.save(user);

        // Respon ringkas untuk frontend (dipakai untuk append row tabel)
        return new Object() {
            public final String result = "OK";
            public final String username = user.getUsername();
            public final String fullName = user.getFullName();
            public final String status = String.valueOf(user.getStatus());
        };
    }

    @PostMapping(path = "/users/forgot", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Object forgotPassword(@RequestBody ForgotPasswordRequest req) {

        // Validasi field
        if (req.getUsernameOrEmail() == null || req.getUsernameOrEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username/Email wajib diisi");
        }
        if (req.getNewPassword() == null || req.getConfirmPassword() == null
                || req.getNewPassword().isBlank() || req.getConfirmPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password & konfirmasi wajib diisi");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password & konfirmasi tidak sama");
        }

        // Cari user by username ATAU email (lowercase untuk konsistensi)
        final String key = req.getUsernameOrEmail().toLowerCase();
        var user = userRepo.findByUsername(key)
                .or(() -> userRepo.findByEmail(key))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User tidak ditemukan"));

        // Hash password baru dengan BCrypt (sesuai skema PROMIS: passwordHash)
        String hash = org.springframework.security.crypto.bcrypt.BCrypt
                .hashpw(req.getNewPassword(), org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10));
        user.setPasswordHash(hash);

        // (Opsional) update timestamp kolom terkait jika ada
        // user.setPasswordUpdatedAt(Instant.now());

        userRepo.save(user);

        // (Opsional) kalau mau paksa logout semua sesi lama, lakukan di layer session store

        // Response ringkas
        return new Object() {
            public final String result = "OK";
            public final String username = user.getUsername();
            public final String message = "Password berhasil direset";
        };
    }

    @PostMapping(path = "/users/inactive", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Object InActiveUser(@RequestBody ForgotPasswordRequest req) {

        // Validasi field
        if (req.getUsernameOrEmail() == null || req.getUsernameOrEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username/Email wajib diisi");
        }


        // Cari user by username ATAU email (lowercase untuk konsistensi)
        final String key = req.getUsernameOrEmail().toLowerCase();
        var user = userRepo.findByUsername(key)
                .or(() -> userRepo.findByEmail(key))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User tidak ditemukan"));

        user.setStatus(UserStatus.valueOf("SUSPENDED"));

        // Hash password baru dengan BCrypt (sesuai skema PROMIS: passwordHash)


        // (Opsional) update timestamp kolom terkait jika ada
        // user.setPasswordUpdatedAt(Instant.now());

        userRepo.save(user);

        // (Opsional) kalau mau paksa logout semua sesi lama, lakukan di layer session store

        // Response ringkas
        return new Object() {
            public final String result = "OK";
            public final String username = user.getUsername();
            public final String message = "user dinonaktifkan";
        };
    }

}

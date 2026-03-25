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
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${api.key}")
    private  String apiKey;


    public GreetingController(AuthServicePlain authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepo = userRepository;
    }


    private boolean isLoggedIn(HttpSession s) {
        return s.getAttribute("AUTH_USER_ID") != null;
    }
    private boolean isAdmin(HttpSession s) {
        return Boolean.TRUE.equals(s.getAttribute("IS_ADMIN"));
    }
    // Simpan tujuan ke session, lalu arahkan ke login
    private String sendToLogin(HttpSession s, String targetPath) {
        s.setAttribute("LOGIN_NEXT", targetPath);
        return "redirect:/middleware/login";
    }
    // Hormati LOGIN_NEXT jika ada; kalau tidak ada, pakai fallback
    private String redirectAfterLogin(HttpSession s, String fallback) {
        Object n = s.getAttribute("LOGIN_NEXT");
        if (n instanceof String next && next.startsWith("/")) {
            s.removeAttribute("LOGIN_NEXT");
            return "redirect:" + next;
        }
        return "redirect:" + fallback;
    }



    @GetMapping("/login")
    public String loginPage(HttpSession session,Model model) {
        if (isLoggedIn(session)) {
            // Sudah login: hormati tujuan (LOGIN_NEXT) jika ada
            String fallback = isAdmin(session) ? "/middleware/dashboard" : "/middleware/sop";
            return redirectAfterLogin(session, fallback);
        }
        if (!model.containsAttribute("error")) model.addAttribute("error", null);
        return "middleware-login";
    }



    @GetMapping("/catalog/login")
    public String catalogLoginPage(HttpSession session, Model model) {
        if (isLoggedIn(session)) {
            return "redirect:/middleware/catalog/dashboard";
        }

        if (!model.containsAttribute("error")) {
            model.addAttribute("error", null);
        }

        return "catalog-login";
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
        // NOTE: pakai flag yang disepakati (contoh: is_admin). Pastikan entity punya getter.
        session.setAttribute("IS_ADMIN", user.isAdmin());

        String fallback = user.isAdmin() ? "/middleware/dashboard" : "/middleware/sop-bo";
        return redirectAfterLogin(session, fallback);
    }

    @PostMapping("/catalog/login")
    public String catalog(@RequestParam String usernameOrEmail,
                          @RequestParam String password,
                          Model model,
                          HttpSession session) {

        MiddlewareUser user = authService.authenticate(usernameOrEmail, password);
        if (user == null) {
            model.addAttribute("error", "Username atau password salah, atau user non-aktif.");
            return "catalog-login";
        }

        session.setAttribute("AUTH_USER_ID", user.getId());
        session.setAttribute("AUTH_USERNAME", user.getUsername());
        session.setAttribute("IS_ADMIN", user.isAdmin());

        String fallback = "/middleware/catalog/dashboard";
        return redirectAfterLogin(session, fallback);
    }

    @GetMapping("/catalog/dashboard")
    public String catalogDashboard(HttpSession session, Model model) {
        Object authUserId = session.getAttribute("AUTH_USER_ID");

        if (authUserId == null) {
            return "redirect:/middleware/catalog/login";
        }

        model.addAttribute("username", session.getAttribute("AUTH_USERNAME"));
        model.addAttribute("isAdmin", Boolean.TRUE.equals(session.getAttribute("IS_ADMIN")));

        return "catalog-dashboard";
    }

    @PostMapping("/logout")
    public String doLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/middleware/login";
    }

    @PostMapping("/catalog/logout")
    public String catalogLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/middleware/catalog/login";
    }

    @GetMapping("/announcements")
    public String pengumuman(HttpSession session,Model model){
        if (!isLoggedIn(session)) {
            return sendToLogin(session, "/middleware/announcements");
        }
        model.addAttribute("apiKey", apiKey);
        return "middleware-announcements";
    }

    @GetMapping("/sop")
    public String manajemenSop(HttpSession session,Model model){
        if (!isLoggedIn(session)) {
            return sendToLogin(session, "/middleware/sop");
        }
        model.addAttribute("apiKey", apiKey);
        return "middleware-sop";
    }



    @GetMapping("/sop-bo")
    public String manajemenSopB0(HttpSession session,Model model){
        if (!isLoggedIn(session)) {
            return sendToLogin(session, "/middleware/sop-bo");
        }
        model.addAttribute("apiKey", apiKey);
        return "middleware-sop-bo";
    }


    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model, HttpServletRequest request) {
        if (!isLoggedIn(session)) {
            return sendToLogin(session, "/middleware/dashboard");
        }
        if (!isAdmin(session)) {
            // Tidak punya akses dashboard → arahkan ke SOP atau ke halaman 403
            return "redirect:/middleware/sop-bo"; // atau: return "redirect:/middleware/forbidden";
        }
        String username = (String) session.getAttribute("AUTH_USERNAME");

        ServletContext ctx = request.getServletContext();

        String  lastScanUser  = (String)  ctx.getAttribute(LoggingFilterConfig.ATTR_LAST_SCAN_USER);
        String  lastScanValue = (String)  ctx.getAttribute(LoggingFilterConfig.ATTR_LAST_SCAN_VALUE);
        Instant lastScanAtRaw = (Instant) ctx.getAttribute(LoggingFilterConfig.ATTR_LAST_SCAN_AT);

        // Konversi Instant -> LocalDateTime supaya gampang diformat di thymeleaf
        LocalDateTime lastScanAt = (lastScanAtRaw == null)
                ? null
                : LocalDateTime.ofInstant(lastScanAtRaw, ZoneId.systemDefault());

        @SuppressWarnings("unchecked")
        var raw = (List<Map<String, Object>>) ctx.getAttribute(LoggingFilterConfig.ATTR_SCAN_TODAY);

        var zone  = java.time.ZoneId.systemDefault();
        var today = java.time.LocalDate.now(zone);

        List<Map<String,Object>> scanToday = (raw == null) ? List.of()
                : raw.stream()
                .filter(m -> {
                    Object t = m.get("time");
                    java.time.LocalDate d = null;
                    if (t instanceof java.time.LocalDateTime ldt) {
                        d = ldt.toLocalDate();
                    } else if (t instanceof java.time.Instant ins) {
                        d = java.time.LocalDateTime.ofInstant(ins, zone).toLocalDate();
                    } else if (t instanceof java.util.Date dt) {
                        d = dt.toInstant().atZone(zone).toLocalDate();
                    } else if (t instanceof CharSequence cs) {
                        // fallback: kalau ada yang tersimpan sebagai String
                        try {
                            d = java.time.LocalDateTime.parse(cs.toString()).toLocalDate();
                        } catch (Exception ignored) {}
                    }
                    return today.equals(d);
                })
                .toList();


        model.addAttribute("username", username);
        model.addAttribute("fullName",username);

        List<User> list = userRepo.findAll().stream()
                .map(data -> com.sarinah.peoplecounter.dto.User.builder()
                        .username(data.getUsername())
                        .fullName(data.getFullName())
                        .status(String.valueOf(data.getStatus()))
                        .keterangan(data.getKeterangan())
                        .build())
                .collect(Collectors.toList());
        model.addAttribute("message", "Welcome to our platform!");
        model.addAttribute("users", list);
        model.addAttribute("username", username);

        model.addAttribute("lastScanUser",  lastScanUser);
        model.addAttribute("lastScanValue", lastScanValue);
        model.addAttribute("lastScanAt",    lastScanAt);
        model.addAttribute("scanToday",     scanToday);
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
                .status(UserStatus.ACTIVE)
                .keterangan(req.getKeterangan())// jika bukan enum: pakai string "ACTIVE"
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

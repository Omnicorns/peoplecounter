package com.sarinah.peoplecounter.controller;



import com.sarinah.peoplecounter.request.ForgotPasswordRequest;
import com.sarinah.peoplecounter.request.LoginRequest;
import com.sarinah.peoplecounter.request.RegisterRequest;
import com.sarinah.peoplecounter.response.AuthResponse;
import com.sarinah.peoplecounter.response.UserResponse;
import com.sarinah.peoplecounter.service.AuthService;
import com.sarinah.peoplecounter.service.LdapUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LdapUserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req){
        authService.register(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login( @RequestBody LoginRequest req,  HttpServletRequest http){
        AuthResponse resp = authService.login(req);
        HttpSession s = http.getSession(true);
        s.setAttribute("AUTH_USERNAME", req.getUsernameOrEmail());
        return ResponseEntity.ok(resp);
    }



//    @PostMapping("/logout")
//    public ResponseEntity<?> logout(@Valid @RequestBody RefreshRequest req){
//        authService.logout(req.refreshToken());
//        return ResponseEntity.noContent().build();
//    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(){
        return ResponseEntity.ok(authService.getAll());
    }

    @PostMapping("/forgot")
    public ResponseEntity<Map<String,String>> forgot(@RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of(
                "message", "Password berhasil diganti, silakan login dengan password baru."
        ));
    }
    @PostMapping("/signin")
    public ResponseEntity<LdapUserService.LoginResponse> login(@RequestBody LdapUserService.LoginRequest req) {
        LdapUserService.LoginResponse res = userService.authenticate(req.username(), req.password());
        return res.success()
                ? ResponseEntity.ok(res)
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }
}

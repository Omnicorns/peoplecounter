package com.sarinah.peoplecounter.controller;


import com.sarinah.peoplecounter.request.ForgotPasswordRequest;
import com.sarinah.peoplecounter.request.LoginRequest;
import com.sarinah.peoplecounter.request.RegisterRequest;
import com.sarinah.peoplecounter.response.AuthResponse;
import com.sarinah.peoplecounter.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req){
        authService.register(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login( @RequestBody LoginRequest req){
        return ResponseEntity.ok(authService.login(req));
    }



//    @PostMapping("/logout")
//    public ResponseEntity<?> logout(@Valid @RequestBody RefreshRequest req){
//        authService.logout(req.refreshToken());
//        return ResponseEntity.noContent().build();
//    }

    @GetMapping("/me")
    public ResponseEntity<String> me(@RequestHeader("Authorization") String auth){
        // If token valid, SecurityConfig sets Authentication with subject=userId
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/forgot")
    public ResponseEntity<Map<String,String>> forgot(@RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of(
                "message", "Password berhasil diganti, silakan login dengan password baru."
        ));
    }
}

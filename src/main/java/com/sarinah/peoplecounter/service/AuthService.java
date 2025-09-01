package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.User;
import com.sarinah.peoplecounter.entity.UserStatus;
import com.sarinah.peoplecounter.repository.UserRepository;
import com.sarinah.peoplecounter.request.LoginRequest;
import com.sarinah.peoplecounter.request.RegisterRequest;
import com.sarinah.peoplecounter.response.AuthResponse;
import com.sarinah.peoplecounter.util.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepo;
    private final JwtService jwt;

    @Value("${app.jwt.refresh-ttl}")
    private  Duration refreshTtl;


    @Transactional
    public void register(RegisterRequest req){
        if(!req.getPassword().equals(req.getPasswordConfirm()))
            throw new IllegalArgumentException("Password & konfirmasi tidak sama");
        if(userRepo.existsByUsername(req.getUsername()))
            throw new IllegalArgumentException("Username sudah dipakai");
        if(userRepo.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email sudah terdaftar");
        if(userRepo.existsByPhone(req.getPhone()))
            throw new IllegalArgumentException("Nomor HP sudah terdaftar");
        if(!req.isAcceptTerms()) throw new IllegalArgumentException("Harus menyetujui Ketentuan Layanan");

        String hash = BCrypt.hashpw(req.getPassword(), BCrypt.gensalt(10));

        User user = User.builder()
                .fullName(StringUtils.normalizeSpace(req.getFullName()))
                .username(req.getUsername().toLowerCase())
                .email(req.getEmail().toLowerCase())
                .phone(req.getPhone())
                .passwordHash(hash)
                .termsAcceptedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .build();
        userRepo.save(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req){
        Optional<User> userOpt = userRepo.findByUsername(req.getUsernameOrEmail().toLowerCase());
        if(userOpt.isEmpty()) userOpt = userRepo.findByEmail(req.getUsernameOrEmail().toLowerCase());
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));
        if(user.getStatus() != UserStatus.ACTIVE) throw new IllegalStateException("Akun tidak aktif");
        if(!BCrypt.checkpw(req.getPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Password salah");

        String access = jwt.generateAccessToken(user.getId().toString(), Map.of(
                "username", user.getUsername(),
                "fullName", user.getFullName()
        ));
        long accessExp = jwt.getAccessTtlSeconds();

        String refreshToken = UUID.randomUUID().toString();


        return new AuthResponse(access, accessExp, refreshToken, refreshTtl.toSeconds());
    }



    @Transactional
    public void logout(String refreshToken){

    }
}

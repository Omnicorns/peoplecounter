package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.User;
import com.sarinah.peoplecounter.entity.UserStatus;
import com.sarinah.peoplecounter.model.ApiContext;
import com.sarinah.peoplecounter.repository.UserRepository;
import com.sarinah.peoplecounter.request.ForgotPasswordRequest;
import com.sarinah.peoplecounter.request.LoginRequest;
import com.sarinah.peoplecounter.request.RegisterRequest;
import com.sarinah.peoplecounter.response.AuthResponse;
import com.sarinah.peoplecounter.response.UserResponse;
import com.sarinah.peoplecounter.util.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Password & konfirmasi tidak sama");
        if(userRepo.existsByUsername(req.getUsername()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Username sudah dipakai");
      //  if(userRepo.existsByEmail(req.getEmail()))
      //      throw new ResponseStatusException(HttpStatus.CONFLICT,"Email sudah terdaftar");
     //   if(userRepo.existsByPhone(req.getPhone()))
     //       throw new ResponseStatusException(HttpStatus.CONFLICT,"Nomor HP sudah terdaftar");
        if(!req.isAcceptTerms()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Harus menyetujui Ketentuan Layanan");

        String hash = BCrypt.hashpw(req.getPassword(), BCrypt.gensalt(10));

        User user = User.builder()
                .fullName(StringUtils.normalizeSpace(req.getFullName()))
                .username(req.getUsername().toLowerCase())
              //  .email(req.getEmail().toLowerCase())
              //  .phone(req.getPhone())

                .passwordHash(hash)
                .termsAcceptedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .keterangan(req.getKeterangan())
                .build();
        userRepo.save(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req){
        Optional<User> userOpt = userRepo.findByUsername(req.getUsernameOrEmail().toLowerCase());
        if(userOpt.isEmpty()) userOpt = userRepo.findByEmail(req.getUsernameOrEmail().toLowerCase());
        User user = userOpt.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"User tidak ditemukan"));
        if(user.getStatus() != UserStatus.ACTIVE)  throw new ResponseStatusException(HttpStatus.CONFLICT," user inactive");;
        if(!BCrypt.checkpw(req.getPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"password salah");

        String access = jwt.generateAccessToken(user.getId().toString(), Map.of(
                "username", user.getUsername(),
                "fullName", user.getFullName()
        ));
        long accessExp = jwt.getAccessTtlSeconds();

        String refreshToken = UUID.randomUUID().toString();
        ApiContext.setUsername(user.getUsername());


        return new AuthResponse(access, accessExp, refreshToken, refreshTtl.toSeconds());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password & konfirmasi tidak sama");
        }

        String key = req.getUsernameOrEmail().toLowerCase();
        User user = userRepo.findByUsername(key)
                .or(() -> userRepo.findByEmail(key))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User tidak ditemukan"));

        String hash = BCrypt.hashpw(req.getNewPassword(), BCrypt.gensalt(10));
        user.setPasswordHash(hash);
        userRepo.save(user);
    }


 public UserResponse getAll(){
     List<com.sarinah.peoplecounter.dto.User> list = userRepo.findAll().stream()
             .map(data-> com.sarinah.peoplecounter.dto.User.builder()
                     .username(data.getUsername())
                     .email(data.getEmail())
                     .status(String.valueOf(data.getStatus()))
                     .build()).collect(Collectors.toList());


        return UserResponse.builder().users(list).build();
 }





}

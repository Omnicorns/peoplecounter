package com.sarinah.peoplecounter.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private String keterangan;
    private String password;
    private String passwordConfirm;
    private boolean acceptTerms;
}

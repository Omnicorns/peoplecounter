package com.sarinah.peoplecounter.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForgotPasswordRequest {
    private String usernameOrEmail;
    private String newPassword;
    private String confirmPassword;
}

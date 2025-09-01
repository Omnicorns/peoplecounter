package com.sarinah.peoplecounter.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
   private String accessToken;
   private long accessTokenExpiresInSeconds;
   private  String refreshToken;
   private  long refreshTokenExpiresInSeconds;
}

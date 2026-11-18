package com.sarinah.peoplecounter.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class LandingController {
    @GetMapping("/portal-sarinah")
   public String halaman() {
      return "landing";
  }
}

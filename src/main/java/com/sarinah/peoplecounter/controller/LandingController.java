package com.sarinah.peoplecounter.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {
    @GetMapping("/portal-sarinah")
   public String halaman() {
      return "landing";
  }
}

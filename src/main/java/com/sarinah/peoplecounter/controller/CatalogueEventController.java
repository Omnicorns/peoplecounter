package com.sarinah.peoplecounter.controller;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class CatalogueEventController {


    @Value("${api.base-url}")
    private String apiBaseUrl;

    @Value("${api.key:}")
    private String apiKey;

    @GetMapping("/topspender89")
    public String catalogue(
            Model model) {

        model.addAttribute("apiBaseUrl", apiBaseUrl);
        model.addAttribute("apiKey", apiKey);

        return "top_spender"; // nama template viewer-mu
    }

    @GetMapping("/topspender910")
    public String catalogue2(
            Model model) {

        model.addAttribute("apiBaseUrl", apiBaseUrl);
        model.addAttribute("apiKey", apiKey);

        return "top_spender2"; // nama template viewer-mu
    }

    @GetMapping("/topspender")
    public String catalogue3(
            Model model) {

        model.addAttribute("apiBaseUrl", apiBaseUrl);
        model.addAttribute("apiKey", apiKey);

        return "top_spender3"; // nama template viewer-mu
    }






}

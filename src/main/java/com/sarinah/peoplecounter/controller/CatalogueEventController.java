package com.sarinah.peoplecounter.controller;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class CatalogueEventController {
    @GetMapping("/SarinahForSEAGames2025")
    public String catalogue(
            Model model) {

        String pdfUrl;
        pdfUrl = "/api/admin/users/pdf/645";



        model.addAttribute("pdfUrl", pdfUrl);
        return "flip2"; // nama template viewer-mu
    }
}

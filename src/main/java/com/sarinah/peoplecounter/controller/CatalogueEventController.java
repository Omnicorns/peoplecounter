package com.sarinah.peoplecounter.controller;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CatalogueEventController {
    @GetMapping("/catalogue/event-mils")
    public String catalogue(
            Model model) {

        String pdfUrl;
        pdfUrl = "/api/admin/users/pdf/643";



        model.addAttribute("pdfUrl", pdfUrl);
        return "flip"; // nama template viewer-mu
    }
}

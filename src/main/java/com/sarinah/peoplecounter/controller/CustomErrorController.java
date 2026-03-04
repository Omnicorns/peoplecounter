package com.sarinah.peoplecounter.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String errorMessage = "Terjadi kesalahan.";

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            switch (statusCode) {
                case 403:
                    errorMessage = "Anda tidak memiliki akses ke halaman ini.";
                    break;
                case 404:
                    errorMessage = "Halaman tidak ditemukan.";
                    break;
                case 500:
                    errorMessage = "Terjadi kesalahan pada server.";
                    break;
            }
            model.addAttribute("statusCode", statusCode);
        }

        model.addAttribute("errorMessage", errorMessage);
        return "error";
    }
}

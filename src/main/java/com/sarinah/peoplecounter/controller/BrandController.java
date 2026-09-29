package com.sarinah.peoplecounter.controller;


import com.sarinah.peoplecounter.model.Baris;
import com.sarinah.peoplecounter.service.BrandService;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.RoundingMode;

/**
 * Unduhan data brand. Tampilannya sudah menyatu di halaman utama sebagai tab,
 * jadi di sini tinggal endpoint CSV-nya.
 */
@Controller
public class BrandController {

    private final BrandService brand;

    public BrandController(BrandService brand) {
        this.brand = brand;
    }

    @GetMapping("/brand.csv")
    public void csv(
            @RequestParam int tahun,
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) String  bank,
            @RequestParam(required = false) String  branch,
            @RequestParam(defaultValue = "false") boolean hanyaKartu,
            HttpServletResponse resp) throws IOException {

        BrandService.Snapshot s = brand.snapshot(tahun, false);
        BrandService.Filter f = new BrandService.Filter(bulan, bank, branch, null, hanyaKartu);

        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"bank-brand-" + tahun + ".csv\"");

        PrintWriter w = resp.getWriter();
        w.write('\ufeff');   // BOM supaya Excel membaca UTF-8
        w.println("Bank Issuer;Group Brand;Nilai;Qty;Struk;Basket Size");

        for (Baris b : brand.bankBrand(s, f)) {
            w.println(bersih(b.getLabel1()) + ';' + bersih(b.getLabel2()) + ';'
                    + b.getNilai().setScale(0, RoundingMode.HALF_UP) + ';'
                    + b.getQty().setScale(0, RoundingMode.HALF_UP)   + ';'
                    + b.getStruk() + ';'
                    + b.getBasket().setScale(0, RoundingMode.HALF_UP));
        }
        w.flush();
    }

    private String bersih(String s) {
        return s == null ? "" : s.replace(';', ',');
    }
}

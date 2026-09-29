package com.sarinah.peoplecounter.controller;


import com.sarinah.peoplecounter.model.Matriks;
import com.sarinah.peoplecounter.service.DataService;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Menyajikan kerangka halaman dan berkas unduhan.
 *
 * Halaman dikirim tanpa menunggu database sama sekali, supaya pengguna langsung
 * melihat tampilan dengan efek loading. Isinya diambil browser lewat
 * /api/dashboard (lihat ApiController).
 */
@Controller
public class DashboardController {

    private final DataService data;

    public DashboardController(DataService data) {
        this.data = data;
    }

    @GetMapping("/dashboard-pos")
    public String halaman() {
        return "index";
    }

    /* =====================================================================
       Unduhan
       ===================================================================== */

    /** Pivot payment apa adanya, siap dibuka di Excel. */
    @GetMapping("/pivot.csv")
    public void pivotCsv(
            @RequestParam int tahun,
            @RequestParam(required = false) String  bank,
            @RequestParam(required = false) String  lokasi,
            @RequestParam(required = false) String  branch,
            @RequestParam(required = false) String  metode,
            @RequestParam(defaultValue = "false") boolean hanyaKartu,
            HttpServletResponse resp) throws IOException {

        DataService.Snapshot s = data.snapshot(tahun, false);
        Matriks m = data.matriks(s, new DataService.Filter(null, bank, lokasi, branch, metode, hanyaKartu));

        siapkanUnduhan(resp, "pivot-payment-" + tahun + ".csv");
        PrintWriter w = resp.getWriter();

        StringBuilder h1 = new StringBuilder("Bank Issuer;Payment Method");
        StringBuilder h2 = new StringBuilder(";");
        for (Matriks.Kolom k : m.getKolom()) {
            h1.append(';').append(csv(k.bulan()));
            h2.append(';').append(csv(k.cardType()));
        }
        h1.append(";Total");
        w.println(h1);
        w.println(h2);

        for (Matriks.Baris b : m.getBaris()) w.println(barisCsv(b));
        w.println(barisCsv(m.getGrandTotal()));
        w.flush();
    }

    /** Data teragregasi -- untuk dibuat pivot sendiri di Excel. */
    @GetMapping("/data.csv")
    public void dataCsv(@RequestParam int tahun, HttpServletResponse resp) throws IOException {
        DataService.Snapshot s = data.snapshot(tahun, false);

        siapkanUnduhan(resp, "data-payment-" + tahun + ".csv");
        PrintWriter w = resp.getWriter();
        w.println("Tahun;Bulan No;Bulan;Bank Issuer;Payment Method;Card Type;Card Class;"
                + "Location;Branch;Floor;Payment Amount;Struk");

        s.data().forEach(x -> w.println(
                tahun + ";" + x.bulanNo() + ";" + csv(x.bulan()) + ";"
              + csv(x.bank())      + ";" + csv(x.metode())    + ";"
              + csv(x.cardType())  + ";" + csv(x.cardClass()) + ";"
              + csv(x.lokasi())    + ";" + csv(x.branch())    + ";" + csv(x.lantai()) + ";"
              + x.nilai().setScale(0, RoundingMode.HALF_UP) + ";"
              + x.trx().setScale(2, RoundingMode.HALF_UP)));
        w.flush();
    }

    private void siapkanUnduhan(HttpServletResponse resp, String namaFile) throws IOException {
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + namaFile + "\"");
        resp.getWriter().write('﻿');   // BOM supaya Excel membaca UTF-8
    }

    private String barisCsv(Matriks.Baris b) {
        StringBuilder sb = new StringBuilder();
        sb.append(csv(b.bank())).append(';').append(csv(b.metode()));
        for (BigDecimal v : b.sel()) sb.append(';').append(v.setScale(0, RoundingMode.HALF_UP));
        sb.append(';').append(b.total().setScale(0, RoundingMode.HALF_UP));
        return sb.toString();
    }

    private String csv(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}

package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.service.ScanTimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/scantime -- isi tab Durasi Pelayanan.
 *
 * Dipisah dari /api/dashboard karena sumbernya tabel lain (pos_order di
 * SARINAH_ODOO, bukan view payment) dan tab ini tidak selalu dibuka. Dengan
 * endpoint tersendiri, datanya baru diambil saat tabnya dibuka.
 */
@RestController
public class ScanTimeController {

    private static final Logger log = LoggerFactory.getLogger(ScanTimeController.class);
    private static final DateTimeFormatter JAM =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    /** Di bawah ini rata-ratanya terlalu mudah terguncang satu transaksi. */
    private static final int MIN_STRUK_KASIR = 20;

    private final ScanTimeService scan;

    public ScanTimeController(ScanTimeService scan) {
        this.scan = scan;
    }

    @GetMapping("/api/scantime")
    public ResponseEntity<Map<String, Object>> scantime(
            @RequestParam(required = false) Integer tahun,
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) String  lokasi,
            @RequestParam(defaultValue = "false") boolean muatUlang) {

        int th = tahun != null ? tahun : LocalDate.now().getYear();

        ScanTimeService.Snapshot s;
        try {
            s = scan.snapshot(th, muatUlang);
        } catch (Exception e) {
            log.error("Gagal mengambil data scan time", e);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error", "Tidak bisa membaca durasi pelayanan dari database. "
                    + "Pastikan tabel sumbernya bisa diakses dari koneksi dashboard.");
            m.put("rinci", e.getMessage());
            return ResponseEntity.ok(m);
        }

        ScanTimeService.Filter f = new ScanTimeService.Filter(bulan, lokasi);

        Map<String, Object> hasil = new LinkedHashMap<>();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tahun",      th);
        meta.put("bulan",      bulan);
        meta.put("satuan",     scan.satuan());
        meta.put("batas",      scan.batas());
        meta.put("waktuAmbil", s.diambil().format(JAM));
        meta.put("detik",      s.detik());
        hasil.put("meta", meta);

        Map<String, Object> pilihan = new LinkedHashMap<>();
        pilihan.put("tahun",  daftarTahun(th));
        pilihan.put("lokasi", scan.daftarLokasi(s));
        hasil.put("pilihan", pilihan);

        Map<String, Object> r = scan.ringkasan(s, f);
        List<Map<String, Object>> kpi = new ArrayList<>();
        kpi.add(kartu("Rata-rata Pelayanan", r.get("rata"),       "durasi", true));
        kpi.add(kartu("Jumlah Struk",        r.get("struk"),      "angka",  false));
        kpi.add(kartu("Jumlah Kasir",        r.get("jml_kasir"),  "angka",  false));
        kpi.add(kartu("Lokasi",              r.get("jml_lokasi"), "angka",  false));
        kpi.add(kartu("Terlama",             r.get("terlama"),    "durasi", false));
        kpi.add(kartu("Pencilan Dibuang",    r.get("pencilan"),   "angka",  false));
        hasil.put("kpi", kpi);

        hasil.put("kasir",  scan.perKasir(s, f, MIN_STRUK_KASIR, 20));
        hasil.put("jam",    scan.perJam(s, f));
        hasil.put("lokasi", scan.perLokasi(s, f));

        return ResponseEntity.ok(hasil);
    }

    /* ===================================================================== */

    /**
     * Tahun berjalan dan dua tahun sebelumnya. Sengaja tidak ditanyakan ke
     * database: SELECT DISTINCT YEAR(...) pada tabel sebesar pos_order berarti
     * memindai seluruh isinya hanya untuk mengisi satu dropdown.
     */
    private List<Integer> daftarTahun(int th) {
        int sekarang = LocalDate.now().getYear();
        int mulai = Math.min(th, sekarang);
        List<Integer> hasil = new ArrayList<>();
        for (int t = Math.max(sekarang, th); t >= mulai - 2; t--) hasil.add(t);
        return hasil;
    }

    private Map<String, Object> kartu(String judul, Object nilai, String format, boolean utama) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("judul",  judul);
        t.put("nilai",  nilai instanceof Number n ? n.doubleValue() : 0d);
        t.put("format", format);
        t.put("utama",  utama);
        return t;
    }
}

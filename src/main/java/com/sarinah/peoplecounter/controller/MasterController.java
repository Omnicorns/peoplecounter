package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.service.PenyalinMaster;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kendali manual untuk salinan master vendor / brand / margin.
 *
 * KENAPA POST, BUKAN GET
 * GET harus aman: boleh diulang, boleh di-cache, boleh di-prefetch browser, dan
 * tidak boleh mengubah apa pun. Penarikan ini menembak Odoo lalu menukar isi
 * tabel -- jelas bukan itu. Kalau dibuat GET, cukup satu crawler, satu tombol
 * refresh yang ketahan, atau satu monitoring yang memanggil tiap menit, dan Odoo
 * dibanjiri tarikan penuh tanpa ada yang bermaksud begitu.
 *
 * Yang GET cuma /status, dan itu memang hanya membaca MySQL.
 *
 * KEAMANAN
 * SecurityConfig memakai anyRequest().authenticated() dengan ApiKeyAuthFilter,
 * jadi kedua endpoint ini otomatis menuntut header X-API-KEY selama
 * /api/master/** TIDAK ditambahkan ke PUBLIC_ENDPOINTS. Jangan ditambahkan.
 *
 * KENAPA 202, BUKAN 200
 * Permintaannya diterima, kerjanya belum selesai. Tarikan penuh dari Odoo lewat
 * linked server memakan belasan detik sampai beberapa menit; menahan koneksi
 * HTTP selama itu berakhir 504 di nginx, persis yang dulu menimpa dashboard.
 * Pemanggil yang ingin tahu hasilnya menengok /status.
 */
@RestController
@RequestMapping("/api/master")
@ConditionalOnProperty(name = "osticket.datasource.enabled", havingValue = "true")
public class MasterController {

    private final PenyalinMaster penyalin;

    public MasterController(PenyalinMaster penyalin) {
        this.penyalin = penyalin;
    }

    /**
     * POST /api/master/segarkan
     *
     *   202 Accepted          penarikan dimulai di latar belakang
     *   409 Conflict          sudah ada penarikan yang sedang berjalan
     *   429 Too Many Requests belum lewat jeda minimal, lihat header Retry-After
     */
    @PostMapping("/segarkan")
    public ResponseEntity<Map<String, Object>> segarkan() {
        PenyalinMaster.Hasil hasil = penyalin.segarkanSekarang();

        Map<String, Object> badan = new LinkedHashMap<>();
        badan.put("hasil", hasil.name());

        return switch (hasil) {
            case DIMULAI -> {
                badan.put("pesan", "Penarikan dari Odoo dimulai di latar belakang. "
                        + "Pantau lewat GET /api/master/status.");
                yield ResponseEntity.accepted().body(badan);
            }
            case SEDANG_BERJALAN -> {
                badan.put("pesan", "Penarikan sedang berjalan. Tunggu sampai selesai.");
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(badan);
            }
            case TERLALU_CEPAT -> {
                long sisa = penyalin.sisaJedaDetik();
                badan.put("pesan", "Baru saja ditarik. Coba lagi dalam " + sisa + " detik. "
                        + "Jeda ini menjaga Odoo dari tarikan beruntun.");
                badan.put("sisaDetik", sisa);
                yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", String.valueOf(sisa))
                        .body(badan);
            }
        };
    }

    /**
     * GET /api/master/status
     *
     * Hanya membaca MySQL, tidak menyentuh Odoo, jadi aman dipanggil sesering apa
     * pun -- termasuk oleh monitoring. Kolom "basi" bernilai true kalau salinannya
     * sudah melewati batas umur; itu yang layak dijadikan alarm.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(penyalin.status());
    }
}

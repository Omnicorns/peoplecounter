package com.sarinah.peoplecounter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Yang mengisi dan menjaga kesegaran memori -- bukan pengguna.
 *
 * Lima detik setelah aplikasi siap, data tahun berjalan diambil, lalu diulang
 * tiap 30 menit. Semuanya di thread penjadwal, jadi tidak menahan apa pun.
 *
 * Karena itu pengguna selalu dilayani dari memori dan tidak pernah menunggu
 * database, sehingga 504 Gateway Time-out tidak bisa terjadi.
 *
 * CATATAN TEKNIS
 * Kelas ini sengaja TIDAK mengimplementasikan interface apa pun dan tidak
 * memakai @Async. Kalau sebuah bean mengimplementasikan interface, Spring
 * membungkusnya dengan proxy berbasis interface, dan @Scheduled pada method
 * yang tidak ada di interface itu akan ditolak saat start:
 *
 *   Need to invoke method 'panaskan' declared on target class 'Pemanas',
 *   but not found in any interface(s) of the exposed proxy type.
 *
 * Pemanggilan pertama diurus initialDelay, jadi ApplicationRunner memang
 * tidak diperlukan.
 *
 * SEBELUM PRESENTASI: jalankan aplikasi lebih awal, lalu tunggu baris
 * "Pemanasan selesai" muncul di log sebelum membuka dashboard.
 */
@Component
@EnableScheduling
public class Pemanas {

    private static final Logger log = LoggerFactory.getLogger(Pemanas.class);

    private final DataService  data;
    private final BrandService brand;

    public Pemanas(DataService data, BrandService brand) {
        this.data  = data;
        this.brand = brand;
    }

    /**
     * Jalan pertama 5 detik setelah aplikasi siap, lalu diulang 30 menit
     * setelah pemanggilan sebelumnya SELESAI -- bukan setelah dimulai,
     * sehingga query yang lama tidak pernah menumpuk.
     */
    @Scheduled(initialDelayString = "${dashboard.jeda-awal-ms:5000}",
            fixedDelayString   = "${dashboard.segarkan-ms:1800000}")
    public void panaskan() {
        int tahun = LocalDate.now().getYear();
        log.info("Memanaskan cache tahun {}...", tahun);

        try {
            data.muatSekarang(tahun);
        } catch (Exception e) {
            log.warn("Pemanasan payment gagal ({}). Akan dicoba lagi 30 menit lagi.",
                    e.getMessage());
        }

        try {
            brand.muatSekarang(tahun);
        } catch (Exception e) {
            log.warn("Pemanasan brand gagal ({}). Akan dicoba lagi 30 menit lagi.",
                    e.getMessage());
        }

        log.info("Pemanasan selesai.");
    }
}
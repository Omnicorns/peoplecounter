package com.sarinah.peoplecounter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Begitu aplikasi jalan, data tahun berjalan langsung diambil di latar belakang
 * supaya pengguna pertama tidak menunggu lama.
 */
@Component
@EnableAsync
public class Pemanas implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Pemanas.class);

    private final DataService data;
    private final BrandService brand;

    public Pemanas(DataService data, BrandService brand) {
        this.data  = data;
        this.brand = brand;
    }

    @Override
    @Async
    public void run(ApplicationArguments args) {
        int tahun = LocalDate.now().getYear();
        try {
            log.info("Memanaskan cache untuk tahun {}...", tahun);
            data.snapshot(tahun, true);
            brand.snapshot(tahun, true);
            log.info("Pemanasan selesai.");
        } catch (Exception e) {
            log.warn("Pemanasan cache gagal ({}). Data akan diambil saat halaman dibuka.",
                     e.getMessage());
        }
    }
}

package com.sarinah.peoplecounter.model;

import java.math.BigDecimal;

/**
 * Data brand. Butuh vw_POSOrderLine, jadi lebih berat daripada data payment
 * dan sengaja dimuat terpisah -- hanya saat halaman brand dibuka.
 *
 * nilai = payment yang dialokasikan proporsional ke tiap brand dalam satu struk,
 *         sehingga totalnya tetap sama dengan laporan payment.
 *
 * Dimensi lantai sengaja tidak dibawa: jumlah barisnya melonjak sementara
 * analisis per lantai sudah tersedia di halaman Lokasi & Lantai.
 */
public record FaktaBrand(
        int        bulanNo,
        String     bulan,
        String     bank,
        String     metode,
        String     branch,
        String     groupBrand,
        String     brand,
        String     mdCategory,
        BigDecimal nilai,
        BigDecimal qty,
        BigDecimal trx) {
}

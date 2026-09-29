package com.sarinah.peoplecounter.model;

import java.math.BigDecimal;

/**
 * Satu baris data teragregasi yang disimpan di memori.
 *
 * Sumbernya hanya vw_POSOrder + vw_POSPayment -- tidak menyentuh order line,
 * jadi isinya setara dengan pivot payment: nilai bayar per bank per kartu.
 *
 * trx = bobot struk. Satu struk yang dibayar 2 kartu berbeda memberi 0.5 ke
 * tiap baris, sehingga dijumlahkan di level mana pun hasilnya tetap jumlah
 * struk sebenarnya.
 */
public record Fakta(
        int        bulanNo,
        String     bulan,
        String     bank,
        String     metode,
        String     cardType,
        String     cardClass,
        String     lokasi,
        String     branch,
        String     lantai,
        BigDecimal nilai,
        BigDecimal trx) {
}

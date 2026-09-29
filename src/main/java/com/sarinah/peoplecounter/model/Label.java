package com.sarinah.peoplecounter.model;

/**
 * Mengganti nilai kosong '(blank)' dengan keterangan yang bisa dipahami.
 *
 * Di data POS, kolom yang kosong punya arti berbeda tergantung konteksnya:
 *   Bank Issuer kosong  -> transaksi non-kartu (Cash, QRIS, Voucher, TMII BANK)
 *   Card Type kosong    -> tidak memakai kartu sama sekali
 *   Card Class kosong   -> kartu terbaca, tapi tingkatannya tidak terdeteksi
 *   Lokasi/Branch/Floor -> tidak tercatat di header order
 */
public final class Label {

    private Label() {}

    public static final String BANK   = "Non-kartu (Cash/QRIS/Voucher)";
    public static final String KARTU  = "Non-kartu";
    public static final String KELAS  = "Kelas tidak terdeteksi";
    public static final String TEMPAT = "Tidak tercatat";
    public static final String METODE = "Metode tidak tercatat";

    public static String bank(String v)   { return kosong(v) ? BANK   : v; }
    public static String kartu(String v)  { return kosong(v) ? KARTU  : v; }
    public static String kelas(String v)  { return kosong(v) ? KELAS  : v; }
    public static String tempat(String v) { return kosong(v) ? TEMPAT : v; }
    public static String metode(String v) { return kosong(v) ? METODE : v; }

    public static boolean kosong(String v) {
        return v == null || v.isBlank() || "(blank)".equals(v);
    }
}

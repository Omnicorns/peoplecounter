package com.sarinah.peoplecounter.model;

import java.util.Locale;
import java.util.Set;

/**
 * Mengganti nilai kosong '(blank)' dengan keterangan yang bisa dipahami.
 *
 * Di data POS, kolom yang kosong punya arti berbeda tergantung konteksnya:
 *   Bank Issuer kosong  -> transaksi non-kartu (Cash, QRIS, Voucher, TMII BANK)
 *                          QRIS yang nama aplikasinya tercatat (GoPay, OVO, ...)
 *                          sudah diisi di DashboardRepository, jadi tidak kosong.
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

    /* ---------------- pembayaran QR ---------------- */

    /** Metode bayar QR dikenali dari namanya yang mengandung "QR" (mis. QRIS). */
    public static boolean metodeQr(String metode) {
        return metode != null && metode.toUpperCase(Locale.ROOT).contains("QR");
    }

    /**
     * Kata yang menandakan penerbit QR adalah bank, bukan aplikasi dompet digital.
     * Dicocokkan per kata utuh, supaya "DANA" tidak tertangkap sebagai "DANAMON".
     */
    private static final Set<String> PENANDA_BANK = Set.of(
            "BANK", "BPD", "BCA", "BNI", "BRI", "BRIMO", "BSI", "BTN", "BTPN", "MANDIRI", "LIVIN",
            "CIMB", "NIAGA", "OCTO", "PERMATA", "DANAMON", "MAYBANK", "OCBC", "PANIN", "MEGA",
            "BJB", "JENIUS", "JAGO", "SEABANK", "UOB", "HSBC", "DBS", "MUAMALAT", "SINARMAS",
            "BUKOPIN", "KBANK", "NOBU", "ALLO");

    public static boolean namaBank(String v) {
        if (kosong(v)) return false;
        for (String kata : v.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")) {
            if (PENANDA_BANK.contains(kata)) return true;
        }
        return false;
    }

    /** Transaksi QR yang nama aplikasinya tercatat dan BUKAN bank. */
    public static boolean aplikasiQr(String penerbit, String metode) {
        return metodeQr(metode) && !kosong(penerbit) && !namaBank(penerbit);
    }
}

package com.sarinah.peoplecounter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Menyalin master vendor / brand / margin dari Odoo ke MySQL osTicket, supaya
 * form D15 Form Promosi mengisi dropdown-nya dari tabel lokal.
 *
 * JALUR DATANYA
 *   Odoo (PostgreSQL)
 *     -> linked server ODOO
 *       -> SARINAHDB, view dbo.vw_OdooVendorBrandMargin   [dibaca jdbcSqlServer]
 *         -> MySQL osTicket, ost_vendor_brand_margin      [ditulis jdbcOsTicket]
 *
 * ODOO DIPANGGIL SESEDIKIT MUNGKIN. Itu aturan utamanya, dan ditegakkan di
 * empat tempat, bukan hanya lewat jadwal:
 *
 *   1. Sekali sehari jam 03:00 WIB. Master ini berubah hitungan hari, bukan jam.
 *   2. Satu panggilan per putaran. Seluruh tabel ditarik dalam satu SELECT --
 *      bukan per vendor, bukan per brand.
 *   3. Pemeriksaan berkala TIDAK langsung menarik. Yang dilihat dulu umur data
 *      di MySQL; kalau masih segar, Odoo tidak disentuh sama sekali. Tanpa ini,
 *      aplikasi yang di-restart sepuluh kali sehari menembak Odoo sepuluh kali.
 *   4. Dua putaran tidak boleh tumpang tindih.
 *
 * KALAU GAGAL, DATA LAMA DIPERTAHANKAN
 * Tabel yang dibaca form tidak pernah dikosongkan lebih dulu. Penyalin mengisi
 * tabel kembaran sampai penuh, baru menukar keduanya dengan satu RENAME TABLE
 * yang atomik. Jadi Odoo mati, jaringan putus, atau tarikan terpotong di tengah
 * -- form tetap memakai salinan kemarin.
 *
 * TIDAK MENGIMPLEMENTASIKAN INTERFACE DAN TIDAK MEMAKAI @Async, dengan alasan
 * yang sama seperti Pemanas: keduanya membuat Spring membungkus bean ini dengan
 * proxy berbasis interface, dan method ber-@Scheduled jadi tidak terlihat saat
 * start.
 */
@Component
@ConditionalOnProperty(name = "osticket.datasource.enabled", havingValue = "true")
public class PenyalinMaster {

    private static final Logger log = LoggerFactory.getLogger(PenyalinMaster.class);

    /* ==========================================================
       SQL
       ========================================================== */

    private static final String KOLOM = """
            vendor_id, vendor_name, brand_id, brand_code, brand_name,
            consignment_margin, location_id, location_name, location_margin""";

    private static final String UMUR =
            "SELECT MAX(disegarkan) FROM ost_vendor_brand_margin";

    private static final String HITUNG =
            "SELECT COUNT(*) FROM ost_vendor_brand_margin";

    private static final String KOSONGKAN =
            "TRUNCATE TABLE ost_vendor_brand_margin_baru";

    /* Yang diisi selalu tabel KEMBARAN, tidak pernah tabel yang dibaca form. */
    private static final String ISI = """
            INSERT INTO ost_vendor_brand_margin_baru
                   (vendor_id, vendor_name, brand_id, brand_code, brand_name,
                    consignment_margin, location_id, location_name, location_margin,
                    disegarkan)
            VALUES (:vendorId, :vendorName, :brandId, :brandCode, :brandName,
                    :consignmentMargin, :locationId, :locationName, :locationMargin,
                    NOW())""";

    /* Tiga arah, satu perintah, atomik. Setelah ini tabel _baru berisi data
       lama dan siap di-TRUNCATE pada putaran berikutnya. */
    private static final String TUKAR = """
            RENAME TABLE ost_vendor_brand_margin      TO ost_vendor_brand_margin_lama,
                         ost_vendor_brand_margin_baru TO ost_vendor_brand_margin,
                         ost_vendor_brand_margin_lama TO ost_vendor_brand_margin_baru""";

    /** Dikirim per seribu baris supaya paket MySQL tidak jebol. */
    private static final int SEKALI_KIRIM = 1_000;

    /* ==========================================================
       bean
       ========================================================== */

    private final NamedParameterJdbcTemplate sqlServer;
    private final NamedParameterJdbcTemplate osticket;
    private final String sumber;
    private final int    umurJam;
    private final int    jedaManualMenit;

    private final AtomicBoolean sedang = new AtomicBoolean(false);

    /** Kapan penarikan manual terakhir DIMULAI. Penjaga jarak antar panggilan. */
    private volatile LocalDateTime manualTerakhir;

    /**
     * @param sumber          view di SARINAHDB yang membungkus OPENQUERY ke Odoo.
     *                        Nilai konfigurasi, bukan masukan pengguna.
     * @param umurJam         batas umur data sebelum dianggap perlu ditarik ulang.
     *                        Bawaannya 20 jam -- lebih pendek dari 24 supaya
     *                        penarikan pagi tetap mendapat data semalam, tapi
     *                        cukup panjang supaya restart beruntun tidak memicu
     *                        panggilan berulang ke Odoo.
     * @param jedaManualMenit jarak minimal antar penarikan manual. Tombol yang
     *                        bisa ditekan berkali-kali adalah cara tercepat
     *                        membanjiri Odoo tanpa disengaja.
     */
    public PenyalinMaster(
            @Qualifier("jdbcSqlServer") NamedParameterJdbcTemplate sqlServer,
            @Qualifier("jdbcOsTicket")  NamedParameterJdbcTemplate osticket,
            @Value("${penyalin.sumber:dbo.vw_OdooVendorBrandMargin}") String sumber,
            @Value("${penyalin.umur-jam:20}")           int umurJam,
            @Value("${penyalin.jeda-manual-menit:15}")  int jedaManualMenit) {
        this.sqlServer       = sqlServer;
        this.osticket        = osticket;
        this.sumber          = sumber;
        this.umurJam         = umurJam;
        this.jedaManualMenit = jedaManualMenit;
    }

    /* ==========================================================
       pemicu
       ========================================================== */

    /** Jam 03:00 WIB tiap hari. Ubah lewat penyalin.cron kalau perlu. */
    @Scheduled(cron = "${penyalin.cron:0 0 3 * * *}", zone = "Asia/Jakarta")
    public void terjadwal() {
        salin("jadwal");
    }

    /**
     * Pemeriksaan berkala. SENGAJA TIDAK LANGSUNG MENARIK -- yang diperiksa umur
     * data yang sudah ada di MySQL. Kalau masih segar, Odoo tidak disentuh sama
     * sekali dan yang tercatat hanya satu baris log.
     *
     * Jalan pertama 20 detik setelah aplikasi siap, lalu tiap 6 jam. Dua gunanya:
     * mengisi tabel yang masih kosong saat pemasangan pertama, dan menjadi jaring
     * pengaman kalau penarikan jam 03:00 gagal.
     */
    @Scheduled(initialDelayString = "${penyalin.jeda-awal-ms:20000}",
               fixedDelayString   = "${penyalin.cek-ms:21600000}")
    public void periksaKesegaran() {
        try {
            Long jam = jamSejakDisegarkan();
            if (jam == null) {
                log.info("Master vendor/brand/margin masih kosong -- menarik sekali dari Odoo.");
                salin("tabel kosong");
            } else if (jam >= umurJam) {
                log.info("Master vendor/brand/margin berumur {} jam (batas {}) -- ditarik ulang.",
                        jam, umurJam);
                salin("data basi");
            } else {
                log.info("Master vendor/brand/margin disegarkan {} jam lalu -- Odoo tidak dipanggil.",
                        jam);
            }
        } catch (Exception e) {
            /* Gagal memeriksa umur bukan alasan menarik dari Odoo membabi buta,
               dan juga bukan alasan menggagalkan apa pun. */
            log.warn("Tidak bisa memeriksa umur master vendor/brand/margin: {}. "
                    + "Penarikan diserahkan ke jadwal.", e.getMessage());
        }
    }

    /* ==========================================================
       inti
       ========================================================== */

    private void salin(String pemicu) {
        if (!sedang.compareAndSet(false, true)) {
            log.info("Penyalinan master sedang berjalan -- permintaan '{}' dilewati.", pemicu);
            return;
        }

        long mulai = System.currentTimeMillis();
        try {
            /* --- satu-satunya panggilan ke Odoo dalam putaran ini --- */
            List<Baris> data = sqlServer.getJdbcTemplate()
                    .query("SELECT " + KOLOM + " FROM " + sumber, PEMETA);

            if (data.isEmpty()) {
                log.warn("Tarikan dari Odoo mengembalikan NOL baris. Penukaran dibatalkan, "
                        + "salinan lama dipertahankan. (pemicu: {})", pemicu);
                return;
            }

            long lama = hitungSekarang();
            if (lama > 0 && (long) data.size() * 2 < lama) {
                log.warn("Tarikan hanya {} baris padahal salinan sekarang {} baris -- kurang dari "
                        + "separuh. Itu tanda tarikan terpotong, bukan tanda data Odoo berkurang. "
                        + "PENUKARAN DIBATALKAN, salinan lama dipertahankan. (pemicu: {})",
                        data.size(), lama, pemicu);
                return;
            }

            osticket.getJdbcTemplate().execute(KOSONGKAN);
            tulisBerkelompok(data);
            osticket.getJdbcTemplate().execute(TUKAR);

            log.info("Master vendor/brand/margin disalin: {} baris, {} detik. Sebelumnya {} baris. "
                    + "(pemicu: {})", data.size(),
                    (System.currentTimeMillis() - mulai) / 1000, lama, pemicu);

        } catch (Exception e) {
            log.error("Penyalinan master GAGAL (pemicu: {}). Salinan lama dipertahankan, "
                    + "form tetap memakai data sebelumnya.", pemicu, e);
        } finally {
            sedang.set(false);
        }
    }

    private void tulisBerkelompok(List<Baris> data) {
        for (int i = 0; i < data.size(); i += SEKALI_KIRIM) {
            List<Baris> potongan = data.subList(i, Math.min(i + SEKALI_KIRIM, data.size()));
            List<SqlParameterSource> param = new ArrayList<>(potongan.size());
            for (Baris b : potongan) {
                param.add(isian(b));
            }
            osticket.batchUpdate(ISI, param.toArray(new SqlParameterSource[0]));
        }
    }

    private static MapSqlParameterSource isian(Baris b) {
        return new MapSqlParameterSource()
                .addValue("vendorId",          b.vendorId())
                .addValue("vendorName",        b.vendorName())
                .addValue("brandId",           b.brandId())
                .addValue("brandCode",         b.brandCode())
                .addValue("brandName",         b.brandName())
                .addValue("consignmentMargin", b.consignmentMargin())
                .addValue("locationId",        b.locationId())
                .addValue("locationName",      b.locationName())
                .addValue("locationMargin",    b.locationMargin());
    }

    private long hitungSekarang() {
        Long n = osticket.getJdbcTemplate().queryForObject(HITUNG, Long.class);
        return n == null ? 0L : n;
    }

    /** null berarti tabelnya masih kosong. */
    private Long jamSejakDisegarkan() {
        Timestamp t = osticket.getJdbcTemplate().queryForObject(UMUR, Timestamp.class);
        if (t == null) return null;
        return Math.max(Duration.between(t.toLocalDateTime(), LocalDateTime.now()).toHours(), 0L);
    }

    /* ==========================================================
       baris
       ========================================================== */

    public record Baris(
            long   vendorId,
            String vendorName,
            long   brandId,
            String brandCode,
            String brandName,
            Double consignmentMargin,
            long   locationId,
            String locationName,
            Double locationMargin) {
    }

    private static final RowMapper<Baris> PEMETA = (ResultSet rs, int nomor) -> new Baris(
            rs.getLong("vendor_id"),
            rs.getString("vendor_name"),
            rs.getLong("brand_id"),
            rs.getString("brand_code"),
            rs.getString("brand_name"),
            angka(rs, "consignment_margin"),
            rs.getLong("location_id"),
            rs.getString("location_name"),
            angka(rs, "location_margin"));

    /**
     * getDouble mengubah NULL menjadi 0, dan margin 0 persen bukan hal yang sama
     * dengan "marginnya belum diisi". Dibedakan di sini.
     */
    private static Double angka(ResultSet rs, String kolom) throws SQLException {
        double v = rs.getDouble(kolom);
        return rs.wasNull() ? null : v;
    }

    /* ==========================================================
       dipakai dari luar
       ========================================================== */

    /** Jawaban untuk permintaan penarikan manual. */
    public enum Hasil {
        /** Penarikan dimulai di latar belakang. */
        DIMULAI,
        /** Sudah ada penarikan yang sedang berjalan. */
        SEDANG_BERJALAN,
        /** Belum lewat jeda minimal sejak penarikan manual sebelumnya. */
        TERLALU_CEPAT
    }

    /**
     * Penarikan manual, untuk vendor baru yang tidak bisa menunggu jadwal besok.
     *
     * LANGSUNG KEMBALI, kerjanya di latar belakang. Tarikan penuh dari Odoo lewat
     * linked server memakan belasan detik sampai beberapa menit; kalau permintaan
     * HTTP ditahan sampai selesai, yang terjadi 504 seperti yang dulu menimpa
     * dashboard.
     */
    public Hasil segarkanSekarang() {
        if (sedang.get()) return Hasil.SEDANG_BERJALAN;

        LocalDateTime sebelumnya = manualTerakhir;
        if (sebelumnya != null
                && Duration.between(sebelumnya, LocalDateTime.now()).toMinutes() < jedaManualMenit) {
            return Hasil.TERLALU_CEPAT;
        }

        /* Dicatat SEBELUM menarik, bukan sesudah. Kalau dicatat sesudah, tombol
           yang ditekan lima kali beruntun lolos kelimanya karena belum ada satu
           pun yang selesai. */
        manualTerakhir = LocalDateTime.now();
        CompletableFuture.runAsync(() -> salin("manual"));
        return Hasil.DIMULAI;
    }

    /** Sisa jeda dalam detik, untuk header Retry-After. */
    public long sisaJedaDetik() {
        LocalDateTime sebelumnya = manualTerakhir;
        if (sebelumnya == null) return 0L;
        long lewat = Duration.between(sebelumnya, LocalDateTime.now()).toSeconds();
        return Math.max(jedaManualMenit * 60L - lewat, 0L);
    }

    /**
     * Keadaan salinan saat ini. HANYA MEMBACA MySQL -- Odoo tidak disentuh, jadi
     * aman dipanggil sesering apa pun, termasuk oleh monitoring.
     */
    public Map<String, Object> status() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("sedangBerjalan", sedang.get());
        try {
            Long jam = jamSejakDisegarkan();
            h.put("jamSejakDisegarkan", jam);
            h.put("jumlahBaris",        hitungSekarang());
            h.put("basi",               jam == null || jam >= umurJam);
        } catch (Exception e) {
            h.put("error", "Tidak bisa membaca tabel salinan: " + e.getMessage());
        }
        h.put("batasUmurJam",  umurJam);
        h.put("sisaJedaDetik", sisaJedaDetik());
        h.put("sumber",        sumber);
        return h;
    }
}

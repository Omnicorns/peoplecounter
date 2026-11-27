package com.sarinah.peoplecounter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durasi pelayanan kasir, dari kolom scan_time_min_timee_order di pos_order.
 *
 * POLANYA SAMA DENGAN DataService: sekali ambil, simpan di memori, semua panel
 * dihitung dari situ, dan permintaan dari browser tidak pernah menunggu
 * database. Yang disimpan BUKAN baris mentah -- jutaan transaksi tidak muat di
 * memori dan tidak perlu -- melainkan hasil agregat per bulan, lokasi, dan
 * kasir. Untuk setahun penuh itu sekitar sepuluh ribu baris.
 *
 * KENAPA RATA-RATA BERBATAS, BUKAN MEDIAN
 * Median memang lebih tahan pencilan, tapi tidak bisa dijumlahkan. Median
 * Januari dan median Februari tidak bisa digabung menjadi median dua bulan,
 * jadi filter "semua bulan" akan memaksa query ulang ke database untuk tiap
 * kombinasi filter -- persis yang membuat dashboard ini dulu kena 504.
 *
 * Yang dipakai: jumlah dan total durasi, keduanya menjumlah dengan benar di
 * rollup mana pun, sehingga rata-ratanya tetap tepat. Pencilan ditangani
 * dengan BATAS ATAS: transaksi yang durasinya melewati batas tidak ikut
 * dihitung rata-ratanya, tapi tetap dicacah terpisah di kolom "pencilan"
 * supaya kelihatan berapa banyak yang dibuang dan kenapa.
 *
 * Batasnya diatur lewat scantime.batas (bawaan 1800). Kasir yang membuka
 * transaksi lalu meninggalkannya menghasilkan durasi ribuan, dan satu kejadian
 * seperti itu cukup menggeser rata-rata seratus transaksi lain.
 */
@Service
public class ScanTimeService {

    private static final Logger log = LoggerFactory.getLogger(ScanTimeService.class);

    /** Angka ini jarang berubah dalam sehari, jadi tidak perlu sesering payment. */
    private static final Duration UMUR = Duration.ofHours(3);

    /* ==========================================================
       SQL
       ==========================================================
       date_order di SARINAH_ODOO tersimpan dalam UTC -- terbukti dari
       sp_SyncOdoo yang menulis dateadd(HH,7,date_order) saat mencatat data
       terakhir. Karena itu batas tanggalnya digeser -7 jam, dan jam
       tampilannya digeser +7. Tanpa itu, transaksi jam 7 pagi WIB terhitung
       di tanggal sebelumnya dan sebaran per jam meleset tujuh jam. */

    private static final String PER_KASIR = """
            SELECT DATEPART(MONTH, DATEADD(HOUR, 7, o.date_order)) AS bulan,
                   ISNULL(NULLIF(LTRIM(RTRIM(o.loc_name)), ''), 'Tidak tercatat') AS lokasi,
                   ISNULL(NULLIF(LTRIM(RTRIM(o.cashier)),  ''), 'Tidak tercatat') AS kasir,
                   SUM(CASE WHEN o.scan_time_min_timee_order <= :batas THEN 1 ELSE 0 END) AS struk,
                   SUM(CASE WHEN o.scan_time_min_timee_order <= :batas
                            THEN CAST(o.scan_time_min_timee_order AS float) ELSE 0 END)   AS total,
                   MAX(CAST(o.scan_time_min_timee_order AS float))                        AS maks,
                   SUM(CASE WHEN o.scan_time_min_timee_order >  :batas THEN 1 ELSE 0 END) AS pencilan
            FROM %s o
            WHERE o.state IN ('done', 'paid', 'invoiced')
              AND o.date_order >= :awal
              AND o.date_order <  :akhir
              AND o.scan_time_min_timee_order IS NOT NULL
              AND o.scan_time_min_timee_order > 0
            GROUP BY DATEPART(MONTH, DATEADD(HOUR, 7, o.date_order)),
                     ISNULL(NULLIF(LTRIM(RTRIM(o.loc_name)), ''), 'Tidak tercatat'),
                     ISNULL(NULLIF(LTRIM(RTRIM(o.cashier)),  ''), 'Tidak tercatat')""";

    private static final String PER_JAM = """
            SELECT DATEPART(MONTH, DATEADD(HOUR, 7, o.date_order)) AS bulan,
                   ISNULL(NULLIF(LTRIM(RTRIM(o.loc_name)), ''), 'Tidak tercatat') AS lokasi,
                   DATEPART(HOUR,  DATEADD(HOUR, 7, o.date_order)) AS jam,
                   COUNT(*)                                        AS struk,
                   SUM(CAST(o.scan_time_min_timee_order AS float)) AS total
            FROM %s o
            WHERE o.state IN ('done', 'paid', 'invoiced')
              AND o.date_order >= :awal
              AND o.date_order <  :akhir
              AND o.scan_time_min_timee_order IS NOT NULL
              AND o.scan_time_min_timee_order > 0
              AND o.scan_time_min_timee_order <= :batas
            GROUP BY DATEPART(MONTH, DATEADD(HOUR, 7, o.date_order)),
                     ISNULL(NULLIF(LTRIM(RTRIM(o.loc_name)), ''), 'Tidak tercatat'),
                     DATEPART(HOUR,  DATEADD(HOUR, 7, o.date_order))""";

    /* ==========================================================
       bean
       ========================================================== */

    private final NamedParameterJdbcTemplate jdbc;
    private final String tabel;
    private final double batas;
    private final String satuan;

    private final Map<Integer, Snapshot> isi    = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicBoolean> sedang = new ConcurrentHashMap<>();

    /**
     * @param tabel nama tabel sumber. Nilai konfigurasi, bukan masukan pengguna.
     *              Bawaannya memakai nama tiga bagian karena jdbcSqlServer
     *              tersambung ke SARINAHDB sementara pos_order ada di
     *              SARINAH_ODOO.
     * @param batas durasi (dalam satuan kolomnya) yang dianggap pencilan.
     * @param satuan hanya untuk label di layar -- "detik" atau "menit".
     */
    public ScanTimeService(
            @Qualifier("jdbcSqlServer") NamedParameterJdbcTemplate jdbc,
            @Value("${scantime.tabel:SARINAH_ODOO.dbo.pos_order}") String tabel,
            @Value("${scantime.batas:1800}")  double batas,
            @Value("${scantime.satuan:detik}") String satuan) {
        this.jdbc   = jdbc;
        this.tabel  = tabel;
        this.batas  = batas;
        this.satuan = satuan;
    }

    public String satuan() {
        return satuan;
    }

    public double batas() {
        return batas;
    }

    /* ==========================================================
       baris agregat
       ========================================================== */

    public record Kasir(int bulan, String lokasi, String kasir,
                        long struk, double total, double maks, long pencilan) {
    }

    public record Jam(int bulan, String lokasi, int jam, long struk, double total) {
    }

    public record Snapshot(int tahun, List<Kasir> kasir, List<Jam> jam,
                           LocalDateTime diambil, long detik) {
        boolean basi() {
            return Duration.between(diambil, LocalDateTime.now()).compareTo(UMUR) > 0;
        }
    }

    /* ==========================================================
       pengambilan
       ========================================================== */

    /**
     * @param paksa tombol "Ambil ulang" -- memicu penyegaran di latar belakang,
     *              bukan menahan pengguna sampai database menjawab.
     */
    public Snapshot snapshot(int tahun, boolean paksa) {
        Snapshot ada = isi.get(tahun);
        if (ada == null) return muat(tahun);          // memori kosong -- terpaksa menunggu
        if (paksa || ada.basi()) segarkanDiLatar(tahun);
        return ada;
    }

    /** Mengambil dan menunggu selesai. Dipakai pemanasan saat aplikasi start. */
    public Snapshot muatSekarang(int tahun) {
        return muat(tahun);
    }

    private synchronized Snapshot muat(int tahun) {
        Snapshot lagi = isi.get(tahun);
        if (lagi != null && !lagi.basi()) return lagi;

        long t0 = System.currentTimeMillis();
        MapSqlParameterSource p = param(tahun);

        List<Kasir> kasir = jdbc.query(PER_KASIR.formatted(tabel), p, (rs, n) -> new Kasir(
                rs.getInt("bulan"), rs.getString("lokasi"), rs.getString("kasir"),
                rs.getLong("struk"), rs.getDouble("total"),
                rs.getDouble("maks"), rs.getLong("pencilan")));

        List<Jam> jam = jdbc.query(PER_JAM.formatted(tabel), p, (rs, n) -> new Jam(
                rs.getInt("bulan"), rs.getString("lokasi"), rs.getInt("jam"),
                rs.getLong("struk"), rs.getDouble("total")));

        long detik = (System.currentTimeMillis() - t0) / 1000;
        Snapshot baru = new Snapshot(tahun, kasir, jam, LocalDateTime.now(), detik);
        isi.put(tahun, baru);
        log.info("Scan time {} dimuat: {} baris kasir, {} baris jam, {} detik",
                tahun, kasir.size(), jam.size(), detik);
        return baru;
    }

    private void segarkanDiLatar(int tahun) {
        AtomicBoolean kunci = sedang.computeIfAbsent(tahun, t -> new AtomicBoolean(false));
        if (!kunci.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                isi.remove(tahun);
                muat(tahun);
            } catch (Exception e) {
                log.warn("Penyegaran scan time {} gagal: {}", tahun, e.getMessage());
            } finally {
                kunci.set(false);
            }
        });
    }

    private MapSqlParameterSource param(int tahun) {
        /* Batas tanggal digeser -7 jam karena date_order tersimpan dalam UTC. */
        LocalDateTime awal  = LocalDateTime.of(tahun,     1, 1, 0, 0).minusHours(7);
        LocalDateTime akhir = LocalDateTime.of(tahun + 1, 1, 1, 0, 0).minusHours(7);
        return new MapSqlParameterSource()
                .addValue("awal",  awal)
                .addValue("akhir", akhir)
                .addValue("batas", batas);
    }

    /* ==========================================================
       filter
       ========================================================== */

    public record Filter(Integer bulan, String lokasi) {

        boolean cocok(Kasir k) {
            if (bulan != null && k.bulan() != bulan) return false;
            return !isi(lokasi) || lokasi.equals(k.lokasi());
        }

        boolean cocok(Jam j) {
            if (bulan != null && j.bulan() != bulan) return false;
            return !isi(lokasi) || lokasi.equals(j.lokasi());
        }

        private static boolean isi(String s) {
            return s != null && !s.isBlank();
        }
    }

    /* ==========================================================
       panel
       ========================================================== */

    public Map<String, Object> ringkasan(Snapshot s, Filter f) {
        long struk = 0, pencilan = 0;
        double total = 0, maks = 0;
        var kasir = new java.util.HashSet<String>();
        var lokasi = new java.util.HashSet<String>();

        for (Kasir k : s.kasir()) {
            if (!f.cocok(k)) continue;
            struk    += k.struk();
            total    += k.total();
            pencilan += k.pencilan();
            maks      = Math.max(maks, k.maks());
            if (k.struk() > 0) kasir.add(k.kasir());
            lokasi.add(k.lokasi());
        }

        Map<String, Object> h = new LinkedHashMap<>();
        h.put("struk",      struk);
        h.put("rata",       struk == 0 ? 0d : total / struk);
        h.put("terlama",    maks);
        h.put("jml_kasir",  kasir.size());
        h.put("jml_lokasi", lokasi.size());
        h.put("pencilan",   pencilan);
        return h;
    }

    /** Kasir dengan rata-rata terlama. Yang struknya sedikit disaring. */
    public List<Map<String, Object>> perKasir(Snapshot s, Filter f, int minStruk, int batasBaris) {
        Map<String, long[]>   cacah = new LinkedHashMap<>();
        Map<String, double[]> nilai = new LinkedHashMap<>();
        Map<String, String>   lokasiDari = new LinkedHashMap<>();

        for (Kasir k : s.kasir()) {
            if (!f.cocok(k) || k.struk() == 0) continue;
            String kunci = k.kasir() + " · " + k.lokasi();
            cacah.computeIfAbsent(kunci, x -> new long[1])[0]   += k.struk();
            nilai.computeIfAbsent(kunci, x -> new double[2])[0] += k.total();
            nilai.get(kunci)[1] = Math.max(nilai.get(kunci)[1], k.maks());
            lokasiDari.putIfAbsent(kunci, k.lokasi());
        }

        List<Map<String, Object>> hasil = new ArrayList<>();
        for (var e : cacah.entrySet()) {
            long n = e.getValue()[0];
            if (n < minStruk) continue;
            double[] v = nilai.get(e.getKey());
            Map<String, Object> baris = new LinkedHashMap<>();
            baris.put("label",   e.getKey());
            baris.put("lokasi",  lokasiDari.get(e.getKey()));
            baris.put("struk",   n);
            baris.put("nilai",   v[0] / n);      // dipakai panjang batang
            baris.put("terlama", v[1]);
            hasil.add(baris);
        }
        hasil.sort(Comparator.comparingDouble(x -> -((Number) x.get("nilai")).doubleValue()));
        return hasil.size() > batasBaris ? new ArrayList<>(hasil.subList(0, batasBaris)) : hasil;
    }

    /** Rata-rata durasi per jam buka, 00..23. Jam tanpa transaksi dilewati. */
    public List<Map<String, Object>> perJam(Snapshot s, Filter f) {
        long[]   struk = new long[24];
        double[] total = new double[24];

        for (Jam j : s.jam()) {
            if (!f.cocok(j)) continue;
            int i = j.jam();
            if (i < 0 || i > 23) continue;
            struk[i] += j.struk();
            total[i] += j.total();
        }

        List<Map<String, Object>> hasil = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            if (struk[i] == 0) continue;
            Map<String, Object> baris = new LinkedHashMap<>();
            baris.put("label", String.format("%02d:00", i));
            baris.put("jam",   i);
            baris.put("struk", struk[i]);
            baris.put("nilai", total[i] / struk[i]);
            hasil.add(baris);
        }
        return hasil;
    }

    /** Ringkas per lokasi, untuk tabel di sebelah grafik. */
    public List<Map<String, Object>> perLokasi(Snapshot s, Filter f) {
        Map<String, long[]>   cacah = new LinkedHashMap<>();
        Map<String, double[]> nilai = new LinkedHashMap<>();

        for (Kasir k : s.kasir()) {
            if (!f.cocok(k) || k.struk() == 0) continue;
            cacah.computeIfAbsent(k.lokasi(), x -> new long[2])[0]   += k.struk();
            cacah.get(k.lokasi())[1] += k.pencilan();
            nilai.computeIfAbsent(k.lokasi(), x -> new double[2])[0] += k.total();
            nilai.get(k.lokasi())[1] = Math.max(nilai.get(k.lokasi())[1], k.maks());
        }

        List<Map<String, Object>> hasil = new ArrayList<>();
        for (var e : cacah.entrySet()) {
            long n = e.getValue()[0];
            double[] v = nilai.get(e.getKey());
            Map<String, Object> baris = new LinkedHashMap<>();
            baris.put("label",    e.getKey());
            baris.put("struk",    n);
            baris.put("nilai",    n == 0 ? 0d : v[0] / n);
            baris.put("terlama",  v[1]);
            baris.put("pencilan", e.getValue()[1]);
            hasil.add(baris);
        }
        hasil.sort(Comparator.comparingDouble(x -> -((Number) x.get("nilai")).doubleValue()));
        return hasil;
    }

    public List<String> daftarLokasi(Snapshot s) {
        return s.kasir().stream().map(Kasir::lokasi).distinct().sorted().toList();
    }
}

package com.sarinah.peoplecounter.controller;



import com.sarinah.peoplecounter.model.Baris;
import com.sarinah.peoplecounter.model.Label;
import com.sarinah.peoplecounter.model.Matriks;
import com.sarinah.peoplecounter.repository.DashboardRepository;
import com.sarinah.peoplecounter.service.BrandService;
import com.sarinah.peoplecounter.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * Seluruh isi dashboard dikirim sebagai JSON.
 *
 * Halaman HTML-nya sendiri tampil seketika tanpa menunggu database; isinya
 * baru diisi setelah panggilan ke sini selesai. Dengan begitu pengguna melihat
 * kerangka halaman lengkap dengan efek loading, bukan layar kosong.
 */
@RestController
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private static final DateTimeFormatter JAM =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    private static final List<String> NAMA_BULAN = List.of(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember");

    /** Palet yang sudah lolos pemeriksaan keterbacaan dan buta warna. */
    private static final List<String> WARNA = List.of(
            "#2563EB", "#1D9E75", "#BA7517", "#7F77DD", "#D4537E");
    private static final String WARNA_LAIN = "#9AA3AF";

    private final DashboardRepository repo;
    private final DataService data;
    private final BrandService brand;

    public ApiController(DashboardRepository repo, DataService data, BrandService brand) {
        this.repo  = repo;
        this.data  = data;
        this.brand = brand;
    }

    @GetMapping("/api/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestParam(required = false) Integer tahun,
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) String  bank,
            @RequestParam(required = false) String  lokasi,
            @RequestParam(required = false) String  branch,
            @RequestParam(required = false) String  metode,
            @RequestParam(defaultValue = "false") boolean hanyaKartu,
            @RequestParam(defaultValue = "false") boolean muatUlang) {

        Map<String, Object> hasil = new LinkedHashMap<>();

        List<Integer> daftarTahun;
        try {
            daftarTahun = repo.daftarTahun();
        } catch (Exception e) {
            log.error("Gagal konek database", e);
            return ResponseEntity.ok(gagal(
                "Tidak bisa terhubung ke database. Periksa pengaturan koneksi di "
              + "application.properties (server, username, password).", e.getMessage()));
        }

        final int th = tahun != null ? tahun
                     : daftarTahun.isEmpty() ? LocalDateTime.now().getYear()
                     : daftarTahun.get(0);
        final boolean ulang = muatUlang;

        /* Dua query berat dijalankan bersamaan. */
        CompletableFuture<DataService.Snapshot> tugasPayment =
                CompletableFuture.supplyAsync(() -> data.snapshot(th, ulang));
        CompletableFuture<Void> tugasBrand =
                CompletableFuture.runAsync(() -> brand.snapshot(th, ulang));

        DataService.Snapshot s;
        try {
            s = tugasPayment.join();
        } catch (Exception e) {
            Throwable sebab = akar(e);
            log.error("Gagal mengambil data tahun {}", th, sebab);
            return ResponseEntity.ok(gagal(
                "Query gagal dijalankan. Pastikan user punya izin SELECT ke "
              + "vw_POSOrder dan vw_POSPayment.", sebab.getMessage()));
        }

        boolean brandSiap = false;
        String brandGagal = null;
        try {
            tugasBrand.join();
            brandSiap = true;
        } catch (Exception e) {
            brandGagal = akar(e).getMessage();
            log.warn("Data brand tidak bisa dimuat: {}", brandGagal);
        }

        DataService.Filter f = new DataService.Filter(bulan, bank, lokasi, branch, metode, hanyaKartu);

        /* ---------- meta & isi filter ---------- */
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tahun",       th);
        meta.put("bulan",       bulan);
        meta.put("namaBulan",   bulan == null ? "Semua bulan" : NAMA_BULAN.get(bulan - 1));
        meta.put("waktuAmbil",  s.diambil().format(JAM));
        meta.put("detik",       s.detik());
        meta.put("jmlBaris",    s.data().size());
        meta.put("brandSiap",   brandSiap);
        meta.put("brandGagal",  brandGagal);
        hasil.put("meta", meta);

        Map<String, Object> pilihan = new LinkedHashMap<>();
        pilihan.put("tahun",  daftarTahun);
        pilihan.put("bulan",  NAMA_BULAN);
        pilihan.put("bank",   data.daftarBank(s));
        pilihan.put("branch", data.daftarBranch(s));
        pilihan.put("lokasi", data.daftarLokasi(s));
        pilihan.put("metode", data.daftarMetode(s));
        hasil.put("pilihan", pilihan);

        /* ---------- KPI ---------- */
        Map<String, Object> ringkasan = data.ringkasan(s, f);
        List<DataService.RingkasBulan> perBulan = data.perBulanRingkas(s, f);

        List<BrandService.RingkasBulan> brandBulan = List.of();
        Map<String, Object> ringkasanBrand = Map.of();
        BrandService.Snapshot bs = null;
        BrandService.Filter bf = null;
        if (brandSiap) {
            bs = brand.snapshot(th, false);
            bf = new BrandService.Filter(bulan, bank, branch, null, hanyaKartu);
            ringkasanBrand = brand.ringkasan(bs, bf);
            brandBulan = brand.perBulanRingkas(bs, bf);
        }

        hasil.put("kpi", susunKpi(ringkasan, ringkasanBrand, perBulan, brandBulan, bulan, brandSiap));

        /* ---------- tren ---------- */
        hasil.put("tren", susunTren(data.grafikBulanan(s, f)));

        /* ---------- daftar & komposisi ---------- */
        List<Baris> perBank   = data.perBank(s, f);
        List<Baris> perMetode = data.perMetode(s, f);
        List<Baris> lokasiNil = data.perLokasi(s, f);
        List<Baris> branchNil = data.perBranch(s, f);
        List<Baris> lantaiNil = data.perLantai(s, f);

        List<Baris> lokasiTrx = urutTrx(lokasiNil);
        List<Baris> branchTrx = urutTrx(branchNil);
        List<Baris> lantaiTrx = urutTrx(lantaiNil);

        hasil.put("bank",      ringkas(perBank, 12, false, Label::bank));
        hasil.put("metode",    komposisi(perMetode, Label::metode));
        hasil.put("kelas",     ringkas(data.perCardClass(s, f), 10, true, Label::kelas));
        hasil.put("lokasi",    ringkas(lokasiNil, 12, false, Label::tempat));
        hasil.put("lantai",    ringkas(lantaiNil, 12, true,  Label::tempat));
        hasil.put("branchtrx", ringkasTrx(branchTrx, 12, false, Label::tempat));
        hasil.put("lantaitrx", ringkasTrx(lantaiTrx, 12, true,  Label::tempat));

        hasil.put("puncak", Map.of(
                "lokasi", puncak(lokasiTrx, false),
                "branch", puncak(branchTrx, false),
                "lantai", puncak(lantaiTrx, true)));

        /* ---------- tabel performa lokasi ---------- */
        hasil.put("tabelLokasi", tabelTempat(lokasiTrx, 8));
        hasil.put("tabelBranch", tabelTempat(branchTrx, 8));

        /* ---------- brand ---------- */
        List<Baris> grup = List.of();
        if (brandSiap) {
            grup = brand.perGroupBrand(bs, bf);
            hasil.put("grup",     ringkas(grup, 10, false, UnaryOperator.identity()));
            hasil.put("brandTop", ringkas(brand.perBrand(bs, bf), 10, false, UnaryOperator.identity()));
            hasil.put("kategori", komposisi(brand.perKategori(bs, bf), UnaryOperator.identity()));
            hasil.put("bankBrand", tabelBankBrand(brand.bankBrand(bs, bf), 15));
        }

        /* ---------- pivot ---------- */
        Matriks m = data.matriks(s, f);
        Map<String, Object> pivot = new LinkedHashMap<>();
        pivot.put("grupBulan", m.getGrupBulan());
        pivot.put("kolom",     m.getKolom());
        pivot.put("baris",     m.getBaris());
        pivot.put("total",     m.getGrandTotal());
        hasil.put("pivot", pivot);

        /* ---------- insight ---------- */
        hasil.put("insight", susunInsight(perBulan, perMetode, grup, lokasiTrx, lantaiTrx));

        return ResponseEntity.ok(hasil);
    }

    /* =====================================================================
       KPI
       ===================================================================== */

    private List<Map<String, Object>> susunKpi(
            Map<String, Object> r,
            Map<String, Object> rb,
            List<DataService.RingkasBulan> perBulan,
            List<BrandService.RingkasBulan> brandBulan,
            Integer bulan,
            boolean brandSiap) {

        List<Double> sNilai  = perBulan.stream().map(x -> x.nilai().doubleValue()).toList();
        List<Double> sStruk  = perBulan.stream().map(x -> x.trx().doubleValue()).toList();
        List<Double> sBasket = perBulan.stream()
                .map(x -> x.trx().signum() == 0 ? 0d
                        : x.nilai().doubleValue() / x.trx().doubleValue()).toList();
        List<Double> sBank   = perBulan.stream().map(x -> (double) x.jmlBank()).toList();
        List<Double> sLokasi = perBulan.stream().map(x -> (double) x.jmlLokasi()).toList();

        List<Double> sQty  = brandBulan.stream().map(x -> x.qty().doubleValue()).toList();
        List<Double> sGrup = brandBulan.stream().map(x -> (double) x.jmlGrup()).toList();
        List<Double> sBrn  = brandBulan.stream().map(x -> (double) x.jmlBrand()).toList();

        List<Map<String, Object>> kpi = new ArrayList<>();
        kpi.add(tile("Nilai Transaksi", r.get("total_nilai"), "rupiah", sNilai,  bulan, true));
        kpi.add(tile("Jumlah Struk",    r.get("total_struk"), "angka",  sStruk,  bulan, false));
        kpi.add(tile("Basket Size",     r.get("basket"),      "rupiah", sBasket, bulan, false));
        kpi.add(tile("Bank Aktif",      r.get("jml_bank"),    "angka",  sBank,   bulan, false));
        kpi.add(tile("Lokasi",          r.get("jml_lokasi"),  "angka",  sLokasi, bulan, false));
        if (brandSiap) {
            kpi.add(tile("Grup Brand", rb.get("jml_group"),  "angka", sGrup, bulan, false));
            kpi.add(tile("Brand",      rb.get("jml_brand"),  "angka", sBrn,  bulan, false));
            kpi.add(tile("Qty Terjual", rb.get("total_qty"), "angka", sQty,  bulan, false));
        }
        return kpi;
    }

    /**
     * Satu kartu KPI. delta dihitung dari deret bulanan:
     *   filter bulan aktif  -> bulan itu dibandingkan bulan sebelumnya
     *   filter bulan kosong -> bulan terakhir dibandingkan bulan sebelumnya
     * Null kalau pembandingnya tidak ada.
     */
    private Map<String, Object> tile(String judul, Object nilai, String format,
                                     List<Double> spark, Integer bulan, boolean utama) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("judul",  judul);
        t.put("nilai",  nilai instanceof BigDecimal b ? b.doubleValue()
                      : nilai instanceof Number n ? n.doubleValue() : 0d);
        t.put("format", format);
        t.put("spark",  spark);
        t.put("utama",  utama);

        Double delta = null;
        String dasar = null;
        if (spark.size() >= 2) {
            int idx = spark.size() - 1;
            double kini = spark.get(idx);
            double lalu = spark.get(idx - 1);
            if (lalu != 0) {
                delta = (kini - lalu) / Math.abs(lalu) * 100;
                dasar = bulan == null ? "bulan terakhir vs sebelumnya" : "vs bulan sebelumnya";
            }
        }
        t.put("delta", delta);
        t.put("dasar", dasar);
        return t;
    }

    /* =====================================================================
       Bagian lain
       ===================================================================== */

    private Map<String, Object> susunTren(DataService.Grafik g) {
        List<Map<String, Object>> seri = new ArrayList<>();
        for (int i = 0; i < g.seri().size(); i++) {
            List<Double> nilai = new ArrayList<>();
            for (DataService.Tumpuk t : g.bulan()) nilai.add(t.nilai().get(i).doubleValue());
            Map<String, Object> satu = new LinkedHashMap<>();
            satu.put("nama",  Label.kartu(g.seri().get(i)));
            satu.put("warna", WARNA.get(i % WARNA.size()));
            satu.put("nilai", nilai);
            seri.add(satu);
        }
        Map<String, Object> hasil = new LinkedHashMap<>();
        hasil.put("label", g.bulan().stream().map(DataService.Tumpuk::bulan).toList());
        hasil.put("total", g.bulan().stream().map(x -> x.total().doubleValue()).toList());
        hasil.put("seri",  seri);
        return hasil;
    }

    private List<Map<String, Object>> tabelTempat(List<Baris> daftar, int batas) {
        List<Map<String, Object>> hasil = new ArrayList<>();
        for (Baris b : daftar) {
            if (hasil.size() >= batas) break;
            Map<String, Object> satu = new LinkedHashMap<>();
            satu.put("label",  Label.tempat(b.getLabel1()));
            satu.put("nilai",  b.getNilai().doubleValue());
            satu.put("struk",  b.getStruk());
            satu.put("basket", b.getBasket().doubleValue());
            satu.put("persen", b.getPersen());
            hasil.add(satu);
        }
        return hasil;
    }

    private List<Map<String, Object>> tabelBankBrand(List<Baris> daftar, int batas) {
        BigDecimal total = daftar.stream().map(Baris::getNilai)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> hasil = new ArrayList<>();
        for (Baris b : daftar) {
            if (hasil.size() >= batas) break;
            Map<String, Object> satu = new LinkedHashMap<>();
            satu.put("bank",  Label.bank(b.getLabel1()));
            satu.put("brand", b.getLabel2());
            satu.put("nilai", b.getNilai().doubleValue());
            satu.put("struk", b.getStruk());
            satu.put("kontribusi", total.signum() == 0 ? 0d
                    : b.getNilai().multiply(BigDecimal.valueOf(100))
                       .divide(total, 2, RoundingMode.HALF_UP).doubleValue());
            hasil.add(satu);
        }
        return hasil;
    }

    private List<Map<String, Object>> susunInsight(
            List<DataService.RingkasBulan> perBulan,
            List<Baris> perMetode,
            List<Baris> grup,
            List<Baris> lokasiTrx,
            List<Baris> lantaiTrx) {

        List<Map<String, Object>> ins = new ArrayList<>();

        if (perBulan.size() >= 2) {
            double kini = perBulan.get(perBulan.size() - 1).nilai().doubleValue();
            double lalu = perBulan.get(perBulan.size() - 2).nilai().doubleValue();
            if (lalu != 0) {
                double d = (kini - lalu) / Math.abs(lalu) * 100;
                ins.add(insight(d >= 0 ? "naik" : "turun", "Pertumbuhan Transaksi",
                        String.format("%+.1f%%", d),
                        "Nilai transaksi bulan terakhir dibandingkan bulan sebelumnya."));
            }
        }

        if (!grup.isEmpty()) {
            ins.add(insight("bintang", "Grup Brand Terbaik", grup.get(0).getLabel1(),
                    "Menyumbang nilai transaksi terbesar pada periode terpilih."));
        }

        if (!lokasiTrx.isEmpty()) {
            Baris b = lokasiTrx.get(0);
            ins.add(insight("lokasi", "Lokasi Paling Ramai", Label.tempat(b.getLabel1()),
                    String.format("%,d transaksi pada periode terpilih.", b.getStruk())
                            .replace(',', '.')));
        }

        if (!lantaiTrx.isEmpty()) {
            Baris b = lantaiTrx.get(0);
            String nama = Label.tempat(b.getLabel1())
                        + (b.getLabel2() == null || b.getLabel2().isBlank()
                           ? "" : " · " + Label.tempat(b.getLabel2()));
            ins.add(insight("lantai", "Lantai Paling Ramai", nama,
                    String.format("%,d transaksi pada periode terpilih.", b.getStruk())
                            .replace(',', '.')));
        }

        if (!perMetode.isEmpty()) {
            Baris b = perMetode.get(0);
            ins.add(insight("kartu", "Metode Dominan", Label.metode(b.getLabel1()),
                    String.format("Menguasai %.1f%% dari seluruh nilai transaksi.", b.getPersen())));
        }

        return ins;
    }

    private Map<String, Object> insight(String ikon, String judul, String sorot, String teks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ikon",  ikon);
        m.put("judul", judul);
        m.put("sorot", sorot);
        m.put("teks",  teks);
        return m;
    }

    /* =====================================================================
       Pembantu
       ===================================================================== */

    private List<Baris> urutTrx(List<Baris> asal) {
        List<Baris> salinan = new ArrayList<>(asal);
        salinan.sort(Comparator.comparingLong(Baris::getStruk).reversed());
        return salinan;
    }

    private List<Map<String, Object>> ringkas(List<Baris> daftar, int batas,
                                              boolean gabung, UnaryOperator<String> pelabel) {
        List<Map<String, Object>> hasil = new ArrayList<>();
        for (Baris b : daftar) {
            if (hasil.size() >= batas) break;
            Map<String, Object> satu = new LinkedHashMap<>();
            satu.put("label",  label(b, gabung, pelabel));
            satu.put("nilai",  b.getNilai().doubleValue());
            satu.put("struk",  b.getStruk());
            satu.put("basket", b.getBasket().doubleValue());
            hasil.add(satu);
        }
        return hasil;
    }

    private List<Map<String, Object>> ringkasTrx(List<Baris> daftar, int batas,
                                                 boolean gabung, UnaryOperator<String> pelabel) {
        List<Map<String, Object>> hasil = new ArrayList<>();
        for (Baris b : daftar) {
            if (hasil.size() >= batas) break;
            Map<String, Object> satu = new LinkedHashMap<>();
            satu.put("label",  label(b, gabung, pelabel));
            satu.put("nilai",  (double) b.getStruk());
            satu.put("rupiah", b.getNilai().doubleValue());
            satu.put("struk",  b.getStruk());
            satu.put("basket", b.getBasket().doubleValue());
            hasil.add(satu);
        }
        return hasil;
    }

    private String label(Baris b, boolean gabung, UnaryOperator<String> pelabel) {
        String s = pelabel.apply(b.getLabel1());
        if (gabung && b.getLabel2() != null && !b.getLabel2().isBlank()) {
            s = s + " · " + pelabel.apply(b.getLabel2());
        }
        return s;
    }

    private Map<String, Object> puncak(List<Baris> daftar, boolean gabung) {
        if (daftar.isEmpty()) return null;
        Baris b = daftar.get(0);
        Map<String, Object> satu = new LinkedHashMap<>();
        satu.put("label",  label(b, gabung, Label::tempat));
        satu.put("struk",  b.getStruk());
        satu.put("nilai",  b.getNilai().doubleValue());
        satu.put("basket", b.getBasket().doubleValue());
        return satu;
    }

    private List<Map<String, Object>> komposisi(List<Baris> daftar, UnaryOperator<String> pelabel) {
        List<Map<String, Object>> hasil = new ArrayList<>();
        double lain = 0;
        for (int i = 0; i < daftar.size(); i++) {
            Baris b = daftar.get(i);
            if (i < WARNA.size()) {
                Map<String, Object> satu = new LinkedHashMap<>();
                satu.put("label", pelabel.apply(b.getLabel1()));
                satu.put("nilai", b.getNilai().doubleValue());
                satu.put("warna", WARNA.get(i));
                hasil.add(satu);
            } else {
                lain += b.getNilai().doubleValue();
            }
        }
        if (lain > 0) {
            Map<String, Object> satu = new LinkedHashMap<>();
            satu.put("label", "Lainnya");
            satu.put("nilai", lain);
            satu.put("warna", WARNA_LAIN);
            hasil.add(satu);
        }
        return hasil;
    }

    private Map<String, Object> gagal(String pesan, String rinci) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", pesan);
        m.put("rinci", rinci);
        return m;
    }

    private Throwable akar(Throwable e) {
        Throwable x = e;
        while (x.getCause() != null && (x instanceof java.util.concurrent.CompletionException
                                     || x instanceof java.util.concurrent.ExecutionException)) {
            x = x.getCause();
        }
        return x;
    }
}

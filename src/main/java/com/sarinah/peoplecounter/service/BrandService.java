package com.sarinah.peoplecounter.service;


import com.sarinah.peoplecounter.model.Baris;
import com.sarinah.peoplecounter.model.FaktaBrand;
import com.sarinah.peoplecounter.repository.DashboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Data brand -- dimuat terpisah dari dashboard utama karena butuh
 * vw_POSOrderLine yang jauh lebih besar.
 *
 * Aturannya sama dengan DataService: permintaan dari browser tidak pernah
 * menunggu database. Kalau data sudah ada di memori, langsung dilayani;
 * penyegaran dikerjakan di latar belakang.
 */
@Service
public class BrandService {

    private static final Logger log = LoggerFactory.getLogger(BrandService.class);

    /** Setelah ini data disegarkan di latar belakang -- bukan ditunggu pengguna. */
    private static final Duration UMUR = Duration.ofHours(2);

    private final DashboardRepository repo;

    private record Kunci(int tahun, Integer bulan) {}

    private final Map<Kunci, Snapshot> cache  = new ConcurrentHashMap<>();
    private final Set<Kunci>           sedang = ConcurrentHashMap.newKeySet();

    public BrandService(DashboardRepository repo) {
        this.repo = repo;
    }

    public record Snapshot(List<FaktaBrand> data, LocalDateTime diambil, long detik) {
        boolean basi() {
            return Duration.between(diambil, LocalDateTime.now()).compareTo(UMUR) > 0;
        }
    }

    /** Sudah ada di memori? Dipakai halaman untuk tahu perlu tombol muat atau tidak. */
    public boolean tersedia(int tahun) {
        return cache.containsKey(new Kunci(tahun, null));
    }

    public Snapshot snapshot(int tahun, boolean paksa) {
        return snapshot(tahun, null, paksa);
    }

    public Snapshot snapshot(int tahun, Integer bulan, boolean paksa) {
        Kunci k = new Kunci(tahun, bulan);
        Snapshot ada = cache.get(k);

        if (ada == null) {
            return muat(k);
        }
        if (paksa || ada.basi()) {
            segarkanDiLatar(k);
        }
        return ada;
    }

    /** Mengambil dari database dan menunggu selesai. Dipakai Pemanas. */
    public Snapshot muatSekarang(int tahun) {
        return muat(new Kunci(tahun, null));
    }

    private synchronized Snapshot muat(Kunci k) {
        Snapshot lagi = cache.get(k);
        if (lagi != null && !lagi.basi()) return lagi;

        long t0 = System.currentTimeMillis();
        log.info("Mengambil data brand {}{}...", k.tahun(),
                k.bulan() == null ? " (setahun)" : " bulan " + k.bulan());

        List<FaktaBrand> data = repo.ambilBrand(k.tahun(), k.bulan());

        long detik = (System.currentTimeMillis() - t0) / 1000;
        log.info("Selesai: {} baris brand, {} detik", data.size(), detik);

        Snapshot baru = new Snapshot(data, LocalDateTime.now(), detik);
        cache.put(k, baru);
        return baru;
    }

    private void segarkanDiLatar(Kunci k) {
        if (!sedang.add(k)) return;
        CompletableFuture.runAsync(() -> {
            try {
                long t0 = System.currentTimeMillis();
                List<FaktaBrand> data = repo.ambilBrand(k.tahun(), k.bulan());
                long detik = (System.currentTimeMillis() - t0) / 1000;
                cache.put(k, new Snapshot(data, LocalDateTime.now(), detik));
                log.info("Brand {} disegarkan: {} baris, {} detik",
                        k.tahun(), data.size(), detik);
            } catch (Exception e) {
                log.warn("Penyegaran brand {} gagal: {}", k.tahun(), e.getMessage());
            } finally {
                sedang.remove(k);
            }
        });
    }

    /* ==========================================================
       filter
       ========================================================== */

    public record Filter(
            Integer bulan,
            String  bank,
            String  branch,
            String  mdCategory,
            boolean hanyaKartu) {

        boolean cocok(FaktaBrand f) {
            if (bulan != null && f.bulanNo() != bulan)                 return false;
            if (isi(bank)       && !bank.equals(f.bank()))             return false;
            if (isi(branch)     && !branch.equals(f.branch()))         return false;
            if (isi(mdCategory) && !mdCategory.equals(f.mdCategory())) return false;
            if (hanyaKartu && !("EDC".equals(f.metode())
                    || "EDC JRF".equals(f.metode())))         return false;
            return true;
        }

        private static boolean isi(String s) {
            return s != null && !s.isBlank();
        }
    }

    /* ==========================================================
       panel
       ========================================================== */

    public Map<String, Object> ringkasan(Snapshot s, Filter f) {
        BigDecimal nilai = BigDecimal.ZERO, qty = BigDecimal.ZERO, trx = BigDecimal.ZERO;
        Set<String> grup = new HashSet<>(), brand = new HashSet<>();

        for (FaktaBrand x : s.data()) {
            if (!f.cocok(x)) continue;
            nilai = nilai.add(nz(x.nilai()));
            qty   = qty.add(nz(x.qty()));
            trx   = trx.add(nz(x.trx()));
            if (!"TANPA ITEM".equals(x.groupBrand())) {
                grup.add(x.groupBrand());
                brand.add(x.brand());
            }
        }

        Map<String, Object> h = new LinkedHashMap<>();
        h.put("total_nilai", nilai);
        h.put("total_qty",   qty.setScale(0, RoundingMode.HALF_UP));
        h.put("total_struk", trx.setScale(0, RoundingMode.HALF_UP));
        h.put("basket", trx.signum() == 0 ? BigDecimal.ZERO
                : nilai.divide(trx, 0, RoundingMode.HALF_UP));
        h.put("jml_group", grup.size());
        h.put("jml_brand", brand.size());
        return h;
    }

    public List<Baris> perGroupBrand(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, FaktaBrand::groupBrand, null, 15);
    }

    public List<Baris> perBrand(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, FaktaBrand::brand, null, 15);
    }

    public List<Baris> perKategori(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, FaktaBrand::mdCategory, null, 99);
    }

    /** Kombinasi bank x group brand terbesar -- bahan utama negosiasi bank. */
    public List<Baris> bankBrand(Snapshot s, Filter f) {
        return kelompok(s, x -> f.cocok(x) && !"(blank)".equals(x.bank()),
                FaktaBrand::bank, FaktaBrand::groupBrand, 150);
    }

    /** Ringkasan per bulan untuk sparkline KPI brand. Filter bulan diabaikan. */
    public record RingkasBulan(int bulanNo, BigDecimal qty, int jmlGrup, int jmlBrand) {}

    public List<RingkasBulan> perBulanRingkas(Snapshot s, Filter f) {
        Filter tanpaBulan = new Filter(null, f.bank(), f.branch(), f.mdCategory(), f.hanyaKartu());
        Map<Integer, BigDecimal>  qty  = new TreeMap<>();
        Map<Integer, Set<String>> grup = new TreeMap<>();
        Map<Integer, Set<String>> brn  = new TreeMap<>();

        for (FaktaBrand x : s.data()) {
            if (!tanpaBulan.cocok(x)) continue;
            int b = x.bulanNo();
            qty.merge(b, nz(x.qty()), BigDecimal::add);
            if (!"TANPA ITEM".equals(x.groupBrand())) {
                grup.computeIfAbsent(b, k -> new HashSet<>()).add(x.groupBrand());
                brn.computeIfAbsent(b, k -> new HashSet<>()).add(x.brand());
            }
        }

        List<RingkasBulan> hasil = new ArrayList<>();
        for (Integer b : qty.keySet()) {
            hasil.add(new RingkasBulan(b, qty.get(b),
                    grup.getOrDefault(b, Set.of()).size(),
                    brn.getOrDefault(b, Set.of()).size()));
        }
        return hasil;
    }

    public List<String> daftarBank(Snapshot s)     { return unik(s, FaktaBrand::bank); }
    public List<String> daftarBranch(Snapshot s)   { return unik(s, FaktaBrand::branch); }
    public List<String> daftarKategori(Snapshot s) { return unik(s, FaktaBrand::mdCategory); }

    /* ==========================================================
       helper
       ========================================================== */

    private List<String> unik(Snapshot s, Function<FaktaBrand, String> ambil) {
        return s.data().stream()
                .map(ambil)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<Baris> kelompok(
            Snapshot s,
            Predicate<FaktaBrand> saring,
            Function<FaktaBrand, String> kunci1,
            Function<FaktaBrand, String> kunci2,
            int batas) {

        Map<String, Baris> per = new LinkedHashMap<>();
        for (FaktaBrand x : s.data()) {
            if (!saring.test(x)) continue;
            String k1 = kunci1.apply(x);
            String k2 = kunci2 == null ? null : kunci2.apply(x);
            String k  = k1 + "\u0000" + (k2 == null ? "" : k2);

            Baris b = per.get(k);
            if (b == null) {
                b = new Baris();
                b.setLabel1(k1);
                b.setLabel2(k2);
                per.put(k, b);
            }
            b.setNilai(b.getNilai().add(nz(x.nilai())));
            b.setQty(b.getQty().add(nz(x.qty())));
            b.setTrx(b.getTrx().add(nz(x.trx())));
        }

        List<Baris> hasil = new ArrayList<>(per.values());
        for (Baris b : hasil) {
            b.setStruk(b.getTrx().setScale(0, RoundingMode.HALF_UP).longValue());
            b.setBasket(b.getTrx().signum() == 0 ? BigDecimal.ZERO
                    : b.getNilai().divide(b.getTrx(), 0, RoundingMode.HALF_UP));
        }
        hasil.sort(Comparator.comparing(Baris::getNilai).reversed());

        BigDecimal total = hasil.stream().map(Baris::getNilai)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() != 0) {
            for (Baris b : hasil) {
                b.setPersen(b.getNilai().multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP).doubleValue());
            }
        }

        return hasil.size() > batas ? new ArrayList<>(hasil.subList(0, batas)) : hasil;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
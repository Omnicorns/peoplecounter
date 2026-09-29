package com.sarinah.peoplecounter.service;


import com.sarinah.peoplecounter.model.Baris;
import com.sarinah.peoplecounter.model.Fakta;
import com.sarinah.peoplecounter.model.Label;
import com.sarinah.peoplecounter.model.Matriks;
import com.sarinah.peoplecounter.repository.DashboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Menyimpan data setahun di memori, lalu menghitung semua panel dari situ.
 * Database hanya disentuh sekali per tahun.
 */
@Service
public class DataService {

    private static final Logger log = LoggerFactory.getLogger(DataService.class);

    /** Data dianggap basi setelah ini, lalu diambil ulang otomatis. */
    private static final Duration UMUR = Duration.ofMinutes(30);

    private final DashboardRepository repo;
    private final Map<Integer, Snapshot> cache = new ConcurrentHashMap<>();

    public DataService(DashboardRepository repo) {
        this.repo = repo;
    }

    /* ==========================================================
       cache
       ========================================================== */

    public record Snapshot(List<Fakta> data, LocalDateTime diambil, long detik) {
        boolean basi() {
            return Duration.between(diambil, LocalDateTime.now()).compareTo(UMUR) > 0;
        }
    }

    public synchronized Snapshot snapshot(int tahun, boolean paksa) {
        Snapshot ada = cache.get(tahun);
        if (ada != null && !paksa && !ada.basi()) return ada;

        long t0 = System.currentTimeMillis();
        log.info("Mengambil data tahun {} dari database...", tahun);

        List<Fakta> data = repo.ambil(tahun);

        long detik = (System.currentTimeMillis() - t0) / 1000;
        log.info("Selesai: {} baris, {} detik", data.size(), detik);

        Snapshot baru = new Snapshot(data, LocalDateTime.now(), detik);
        cache.put(tahun, baru);
        return baru;
    }

    /* ==========================================================
       filter
       ========================================================== */

    public record Filter(
            Integer bulan,
            String  bank,
            String  lokasi,
            String  branch,
            String  metode,
            boolean hanyaKartu) {

        boolean cocok(Fakta f) {
            if (bulan != null && f.bulanNo() != bulan) return false;
            return cocokDasar(f, true);
        }

        /** Mengabaikan filter bulan -- dipakai panel yang bulannya jadi kolom. */
        boolean cocokTanpaBulan(Fakta f) {
            return cocokDasar(f, true);
        }

        /** Mengabaikan filter metode -- supaya komposisi metode tetap utuh. */
        boolean cocokTanpaMetode(Fakta f) {
            if (bulan != null && f.bulanNo() != bulan) return false;
            return cocokDasar(f, false);
        }

        private boolean cocokDasar(Fakta f, boolean pakaiMetode) {
            if (isi(bank)   && !bank.equals(f.bank()))     return false;
            if (isi(lokasi) && !lokasi.equals(f.lokasi())) return false;
            if (isi(branch) && !branch.equals(f.branch())) return false;
            if (pakaiMetode) {
                if (isi(metode) && !metode.equals(f.metode())) return false;
                if (hanyaKartu  && !("EDC".equals(f.metode())
                                  || "EDC JRF".equals(f.metode()))) return false;
            }
            return true;
        }

        private static boolean isi(String s) {
            return s != null && !s.isBlank();
        }
    }

    /* ==========================================================
       kartu ringkasan
       ========================================================== */

    public Map<String, Object> ringkasan(Snapshot s, Filter f) {
        BigDecimal nilai = BigDecimal.ZERO;
        BigDecimal trx   = BigDecimal.ZERO;
        Set<String> bank   = new HashSet<>();
        Set<String> lokasi = new HashSet<>();

        for (Fakta x : s.data()) {
            if (!f.cocok(x)) continue;
            nilai = nilai.add(nz(x.nilai()));
            trx   = trx.add(nz(x.trx()));
            if (!"(blank)".equals(x.bank())) bank.add(x.bank());
            lokasi.add(x.lokasi());
        }

        Map<String, Object> hasil = new LinkedHashMap<>();
        hasil.put("total_nilai", nilai);
        hasil.put("total_struk", trx.setScale(0, RoundingMode.HALF_UP));
        hasil.put("basket", trx.signum() == 0
                ? BigDecimal.ZERO
                : nilai.divide(trx, 0, RoundingMode.HALF_UP));
        hasil.put("jml_bank",   bank.size());
        hasil.put("jml_lokasi", lokasi.size());
        return hasil;
    }

    /* ==========================================================
       panel tabel
       ========================================================== */

    public List<Baris> perBank(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, Fakta::bank, null, 25, true);
    }

    public List<Baris> perMetode(Snapshot s, Filter f) {
        return kelompok(s, f::cocokTanpaMetode, Fakta::metode, null, 99, true);
    }

    public List<Baris> perLokasi(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, Fakta::lokasi, null, 99, true);
    }

    public List<Baris> perBranch(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, Fakta::branch, null, 99, true);
    }

    /** Lantai, digabung dengan branch supaya "Lantai 3" tidak tercampur antar gedung. */
    public List<Baris> perLantai(Snapshot s, Filter f) {
        return kelompok(s, f::cocok, Fakta::lantai, Fakta::branch, 99, true);
    }

    public List<Baris> perCardClass(Snapshot s, Filter f) {
        List<Baris> hasil = kelompok(s,
                x -> f.cocok(x) && !"(blank)".equals(x.cardClass()),
                Fakta::cardClass, Fakta::cardType, 999, true);
        hasil.removeIf(b -> b.getStruk() < 20);
        hasil.sort(Comparator.comparing(Baris::getBasket).reversed());
        return potong(hasil, 15);
    }

    public List<Baris> trenBulanan(Snapshot s, Filter f) {
        Map<Integer, Baris> per = new TreeMap<>();
        for (Fakta x : s.data()) {
            if (!f.cocokTanpaBulan(x)) continue;
            tambah(per.computeIfAbsent(x.bulanNo(), k -> baru(x.bulan())), x);
        }
        List<Baris> hasil = new ArrayList<>(per.values());
        selesaikan(hasil, false);
        return hasil;
    }

    /* ==========================================================
       ringkasan per bulan -- untuk sparkline dan perbandingan periode
       ========================================================== */

    public record RingkasBulan(
            int        bulanNo,
            String     bulan,
            BigDecimal nilai,
            BigDecimal trx,
            int        jmlBank,
            int        jmlLokasi) {}

    /** Selalu setahun penuh: filter bulan diabaikan. */
    public List<RingkasBulan> perBulanRingkas(Snapshot s, Filter f) {
        Map<Integer, String>          nama  = new TreeMap<>();
        Map<Integer, BigDecimal[]>    angka = new TreeMap<>();
        Map<Integer, Set<String>>     bank  = new TreeMap<>();
        Map<Integer, Set<String>>     lok   = new TreeMap<>();

        for (Fakta x : s.data()) {
            if (!f.cocokTanpaBulan(x)) continue;
            int b = x.bulanNo();
            nama.putIfAbsent(b, x.bulan());
            BigDecimal[] a = angka.computeIfAbsent(b,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(nz(x.nilai()));
            a[1] = a[1].add(nz(x.trx()));
            if (!"(blank)".equals(x.bank())) {
                bank.computeIfAbsent(b, k -> new HashSet<>()).add(x.bank());
            }
            lok.computeIfAbsent(b, k -> new HashSet<>()).add(x.lokasi());
        }

        List<RingkasBulan> hasil = new ArrayList<>();
        for (Map.Entry<Integer, String> e : nama.entrySet()) {
            int b = e.getKey();
            BigDecimal[] a = angka.get(b);
            hasil.add(new RingkasBulan(b, e.getValue(), a[0], a[1],
                    bank.getOrDefault(b, Set.of()).size(),
                    lok.getOrDefault(b, Set.of()).size()));
        }
        return hasil;
    }

    /* ==========================================================
       data untuk grafik kolom bertumpuk: bulan x Card Type
       ========================================================== */

    public record Tumpuk(String bulan, List<BigDecimal> nilai, BigDecimal total) {}

    public record Grafik(List<String> seri, List<Tumpuk> bulan, BigDecimal maks) {}

    public Grafik grafikBulanan(Snapshot s, Filter f) {
        TreeMap<Integer, String> bulanAda = new TreeMap<>();
        TreeSet<String>          seriAda  = new TreeSet<>(URUT_KARTU);
        Map<String, BigDecimal>  sel      = new HashMap<>();

        for (Fakta x : s.data()) {
            if (!f.cocokTanpaBulan(x)) continue;
            bulanAda.put(x.bulanNo(), x.bulan());
            seriAda.add(x.cardType());
            sel.merge(x.bulanNo() + "\u0000" + x.cardType(), nz(x.nilai()), BigDecimal::add);
        }

        List<String> seri = new ArrayList<>(seriAda);
        List<Tumpuk> hasil = new ArrayList<>();
        BigDecimal maks = BigDecimal.ZERO;

        for (Map.Entry<Integer, String> b : bulanAda.entrySet()) {
            List<BigDecimal> isi = new ArrayList<>(seri.size());
            BigDecimal total = BigDecimal.ZERO;
            for (String k : seri) {
                BigDecimal v = sel.getOrDefault(b.getKey() + "\u0000" + k, BigDecimal.ZERO);
                isi.add(v);
                total = total.add(v);
            }
            if (total.compareTo(maks) > 0) maks = total;
            hasil.add(new Tumpuk(b.getValue(), isi, total));
        }
        return new Grafik(seri, hasil, maks);
    }

    /* ==========================================================
       tabel silang meniru pivot payment
       ========================================================== */

    public Matriks matriks(Snapshot s, Filter f) {
        Map<String, BigDecimal>  sel      = new HashMap<>();
        TreeMap<Integer, String> bulanAda = new TreeMap<>();
        TreeSet<String>          kartuAda = new TreeSet<>(URUT_KARTU);
        TreeSet<String>          barisAda = new TreeSet<>(URUT_BARIS);

        for (Fakta x : s.data()) {
            if (!f.cocokTanpaBulan(x)) continue;
            bulanAda.put(x.bulanNo(), x.bulan());
            kartuAda.add(x.cardType());
            barisAda.add(x.bank() + "\u0000" + x.metode());
            sel.merge(x.bulanNo() + "\u0000" + x.cardType()
                    + "\u0000" + x.bank() + "\u0000" + x.metode(),
                    nz(x.nilai()), BigDecimal::add);
        }

        List<Matriks.Kolom>     kolom     = new ArrayList<>();
        List<String>            kartuAsli = new ArrayList<>();
        List<Matriks.GrupBulan> grup      = new ArrayList<>();
        for (Map.Entry<Integer, String> b : bulanAda.entrySet()) {
            for (String kartu : kartuAda) {
                kolom.add(new Matriks.Kolom(b.getKey(), b.getValue(), Label.kartu(kartu)));
                kartuAsli.add(kartu);
            }
            grup.add(new Matriks.GrupBulan(b.getValue(), kartuAda.size()));
        }

        List<Matriks.Baris> baris = new ArrayList<>();
        BigDecimal[] totalKolom = new BigDecimal[kolom.size()];
        Arrays.fill(totalKolom, BigDecimal.ZERO);
        BigDecimal totalSemua = BigDecimal.ZERO;

        for (String kunci : barisAda) {
            String[] p = kunci.split("\u0000", -1);
            String bank   = p[0];
            String metode = p.length > 1 ? p[1] : "";

            List<BigDecimal> isi = new ArrayList<>(kolom.size());
            BigDecimal totalBaris = BigDecimal.ZERO;

            for (int i = 0; i < kolom.size(); i++) {
                Matriks.Kolom k = kolom.get(i);
                BigDecimal v = sel.getOrDefault(
                        k.bulanNo() + "\u0000" + kartuAsli.get(i)
                      + "\u0000" + bank + "\u0000" + metode, BigDecimal.ZERO);
                isi.add(v);
                totalBaris    = totalBaris.add(v);
                totalKolom[i] = totalKolom[i].add(v);
            }
            totalSemua = totalSemua.add(totalBaris);
            baris.add(new Matriks.Baris(Label.bank(bank), Label.metode(metode), isi, totalBaris));
        }

        return new Matriks(grup, kolom, baris,
                new Matriks.Baris("Grand Total", "", Arrays.asList(totalKolom), totalSemua));
    }

    /* ==========================================================
       daftar isi dropdown
       ========================================================== */

    public List<String> daftarBank(Snapshot s)   { return unik(s, Fakta::bank); }
    public List<String> daftarLokasi(Snapshot s) { return unik(s, Fakta::lokasi); }
    public List<String> daftarMetode(Snapshot s) { return unik(s, Fakta::metode); }
    public List<String> daftarBranch(Snapshot s) { return unik(s, Fakta::branch); }

    private List<String> unik(Snapshot s, Function<Fakta, String> ambil) {
        return s.data().stream()
                .map(ambil)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .sorted(URUT_KARTU)
                .toList();
    }

    /* ==========================================================
       helper
       ========================================================== */

    private List<Baris> kelompok(
            Snapshot s,
            Predicate<Fakta> saring,
            Function<Fakta, String> kunci1,
            Function<Fakta, String> kunci2,
            int batas,
            boolean hitungBasket) {

        Map<String, Baris> per = new LinkedHashMap<>();
        for (Fakta x : s.data()) {
            if (!saring.test(x)) continue;
            String k1 = kunci1.apply(x);
            String k2 = kunci2 == null ? null : kunci2.apply(x);
            String k  = k1 + "\u0000" + (k2 == null ? "" : k2);

            Baris b = per.get(k);
            if (b == null) {
                b = baru(k1);
                b.setLabel2(k2);
                per.put(k, b);
            }
            tambah(b, x);
        }

        List<Baris> hasil = new ArrayList<>(per.values());
        selesaikan(hasil, hitungBasket);
        hasil.sort(Comparator.comparing(Baris::getNilai).reversed());
        return potong(hasil, batas);
    }

    private List<Baris> potong(List<Baris> daftar, int batas) {
        return daftar.size() > batas
             ? new ArrayList<>(daftar.subList(0, batas))
             : daftar;
    }

    private Baris baru(String label) {
        Baris b = new Baris();
        b.setLabel1(label);
        return b;
    }

    private void tambah(Baris b, Fakta x) {
        b.setNilai(b.getNilai().add(nz(x.nilai())));
        b.setTrx(b.getTrx().add(nz(x.trx())));
    }

    private void selesaikan(List<Baris> daftar, boolean hitungBasket) {
        for (Baris b : daftar) {
            b.setStruk(b.getTrx().setScale(0, RoundingMode.HALF_UP).longValue());
            if (hitungBasket) {
                b.setBasket(b.getTrx().signum() == 0
                        ? BigDecimal.ZERO
                        : b.getNilai().divide(b.getTrx(), 0, RoundingMode.HALF_UP));
            }
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** (blank) selalu di depan, sisanya urut abjad. */
    private static final Comparator<String> URUT_KARTU = (a, b) -> {
        boolean ka = "(blank)".equals(a), kb = "(blank)".equals(b);
        if (ka != kb) return ka ? -1 : 1;
        return a.compareToIgnoreCase(b);
    };

    /** Baris pivot: bank dulu, lalu metode. */
    private static final Comparator<String> URUT_BARIS = (a, b) -> {
        String[] pa = a.split("\u0000", -1);
        String[] pb = b.split("\u0000", -1);
        int c = URUT_KARTU.compare(pa[0], pb[0]);
        if (c != 0) return c;
        return pa.length > 1 && pb.length > 1 ? pa[1].compareToIgnoreCase(pb[1]) : 0;
    };
}

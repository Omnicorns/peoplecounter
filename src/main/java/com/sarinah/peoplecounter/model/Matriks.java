package com.sarinah.peoplecounter.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tabel silang yang meniru pivot payment:
 *   Rows    = Bank Issuer -> Payment Method
 *   Columns = Bulan -> Card Type
 *   Values  = Sum of Payment Amount
 */
public class Matriks {

    /** Satu kolom data: satu bulan, satu jenis kartu. */
    public record Kolom(int bulanNo, String bulan, String cardType) {}

    /** Header bulan di baris atas, membentang beberapa kolom Card Type. */
    public record GrupBulan(String bulan, int jumlahKolom) {}

    /** Satu baris: satu bank + satu metode bayar. */
    public record Baris(
            String           bank,
            String           metode,
            List<BigDecimal> sel,
            BigDecimal       total) {}

    private final List<GrupBulan> grupBulan;
    private final List<Kolom>     kolom;
    private final List<Baris>     baris;
    private final Baris           grandTotal;

    public Matriks(List<GrupBulan> grupBulan, List<Kolom> kolom,
                   List<Baris> baris, Baris grandTotal) {
        this.grupBulan  = grupBulan;
        this.kolom      = kolom;
        this.baris      = baris;
        this.grandTotal = grandTotal;
    }

    public List<GrupBulan> getGrupBulan()  { return grupBulan; }
    public List<Kolom>     getKolom()      { return kolom; }
    public List<Baris>     getBaris()      { return baris; }
    public Baris           getGrandTotal() { return grandTotal; }
    public boolean         isKosong()      { return baris.isEmpty(); }
}

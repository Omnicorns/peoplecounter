package com.sarinah.peoplecounter.model;

import java.math.BigDecimal;

/** Satu baris hasil agregasi. Dipakai untuk semua tabel di dashboard. */
public class Baris {

    private String label1;
    private String label2;
    private BigDecimal nilai   = BigDecimal.ZERO;
    private BigDecimal qty     = BigDecimal.ZERO;
    private long       struk;
    private BigDecimal basket  = BigDecimal.ZERO;
    private BigDecimal trx     = BigDecimal.ZERO;
    private double     persen;

    public String getLabel1()            { return label1; }
    public void   setLabel1(String v)    { this.label1 = v; }

    public String getLabel2()            { return label2; }
    public void   setLabel2(String v)    { this.label2 = v; }

    public BigDecimal getNilai()         { return nilai; }
    public void setNilai(BigDecimal v)   { this.nilai = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getQty()           { return qty; }
    public void setQty(BigDecimal v)     { this.qty = v == null ? BigDecimal.ZERO : v; }

    public long getStruk()               { return struk; }
    public void setStruk(long v)         { this.struk = v; }

    public BigDecimal getBasket()        { return basket; }
    public void setBasket(BigDecimal v)  { this.basket = v == null ? BigDecimal.ZERO : v; }

    /** Bobot struk sebelum dibulatkan. */
    public BigDecimal getTrx()           { return trx; }
    public void setTrx(BigDecimal v)     { this.trx = v == null ? BigDecimal.ZERO : v; }

    public double getPersen()            { return persen; }
    public void   setPersen(double v)    { this.persen = v; }
}

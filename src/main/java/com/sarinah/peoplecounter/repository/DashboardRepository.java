package com.sarinah.peoplecounter.repository;


import com.sarinah.peoplecounter.model.Fakta;
import com.sarinah.peoplecounter.model.FaktaBrand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Satu query saja, hanya menyentuh dua view:
 *   dbo.vw_POSOrder    -- tanggal dan lokasi
 *   dbo.vw_POSPayment  -- nilai bayar, bank, jenis & kelas kartu
 *
 * vw_POSOrderLine sengaja TIDAK dipakai. Itulah bagian terberat sebelumnya,
 * dan untuk laporan payment per bank memang tidak diperlukan.
 *
 * Hasilnya setara dengan pivot payment: satu baris per
 * bulan x bank x metode x jenis kartu x kelas kartu x lokasi.
 */
@Repository
public class DashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(@Qualifier("jdbcSqlServer") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SQL = """
        WITH ord AS (
            SELECT
                o.order_id,
                CAST(o.[Order Date] AS date) AS OrderDate,
                o.[Location],
                o.[Branch],
                o.[Floor]
            FROM dbo.vw_POSOrder o
            WHERE o.[Order Date] >= :dtFrom
              AND o.[Order Date] <  :dtTo
        ),
        pay AS (
            /* 1 baris per order + bank + kartu -- menangani split bayar */
            SELECT
                p.order_id,
                p.[Payment Method],
                p.[Bank Issuer],
                p.[Card Type],
                p.[Card Class],
                SUM(p.[Payment Amount]) AS nilai
            FROM dbo.vw_POSPayment p
            WHERE EXISTS (SELECT 1 FROM ord WHERE ord.order_id = p.order_id)
            GROUP BY
                p.order_id, p.[Payment Method], p.[Bank Issuer],
                p.[Card Type], p.[Card Class]
        ),
        gabung AS (
            SELECT
                MONTH(ord.OrderDate)                    AS bulan_no,
                DATENAME(MONTH, ord.OrderDate)          AS bulan,
                ISNULL(pay.[Bank Issuer],    '(blank)') AS bank,
                ISNULL(pay.[Payment Method], '(blank)') AS metode,
                ISNULL(pay.[Card Type],      '(blank)') AS card_type,
                ISNULL(pay.[Card Class],     '(blank)') AS card_class,
                ISNULL(ord.[Location],       '(blank)') AS lokasi,
                ISNULL(ord.[Branch],         '(blank)') AS branch,
                ISNULL(ord.[Floor],          '(blank)') AS lantai,
                pay.order_id,
                pay.nilai
            FROM pay
            JOIN ord ON ord.order_id = pay.order_id
        ),
        bobot AS (
            /* berapa baris yang dihasilkan satu struk -> untuk membagi bobotnya */
            SELECT order_id, COUNT(*) AS n
            FROM gabung
            GROUP BY order_id
        )
        SELECT
            g.bulan_no,
            g.bulan,
            g.bank,
            g.metode,
            g.card_type,
            g.card_class,
            g.lokasi,
            g.branch,
            g.lantai,
            SUM(g.nilai)                            AS nilai,
            SUM(CAST(1.0 AS decimal(19,9)) / b.n)   AS trx
        FROM gabung g
        JOIN bobot  b ON b.order_id = g.order_id
        GROUP BY
            g.bulan_no, g.bulan, g.bank, g.metode,
            g.card_type, g.card_class, g.lokasi, g.branch, g.lantai
        """;

    private static final RowMapper<Fakta> MAP = (rs, i) -> new Fakta(
            rs.getInt("bulan_no"),
            rs.getString("bulan"),
            rs.getString("bank"),
            rs.getString("metode"),
            rs.getString("card_type"),
            rs.getString("card_class"),
            rs.getString("lokasi"),
            rs.getString("branch"),
            rs.getString("lantai"),
            rs.getBigDecimal("nilai"),
            rs.getBigDecimal("trx"));

    public List<Fakta> ambil(int tahun) {
        return jdbc.query(SQL, new MapSqlParameterSource()
                .addValue("dtFrom", LocalDate.of(tahun, 1, 1))
                .addValue("dtTo",   LocalDate.of(tahun + 1, 1, 1)), MAP);
    }

    /* =====================================================================
       DATA BRAND -- memakai vw_POSOrderLine, jadi lebih berat.
       Dipanggil terpisah, hanya saat halaman brand dibuka.

       Nilai payment dialokasikan proporsional ke tiap brand dalam satu struk.
       Order tanpa item, atau yang total barangnya nol, masuk 'TANPA ITEM'
       dengan nilai utuh supaya totalnya tetap rekonsiliasi.
       ===================================================================== */

    private static final String SQL_BRAND = """
        WITH ord AS (
            SELECT
                o.order_id,
                CAST(o.[Order Date] AS date) AS OrderDate,
                o.[Branch]
            FROM dbo.vw_POSOrder o
            WHERE o.[Order Date] >= :dtFrom
              AND o.[Order Date] <  :dtTo
        ),
        pay AS (
            SELECT
                p.order_id,
                p.[Payment Method],
                p.[Bank Issuer],
                SUM(p.[Payment Amount]) AS Payment_Amount
            FROM dbo.vw_POSPayment p
            WHERE EXISTS (SELECT 1 FROM ord WHERE ord.order_id = p.order_id)
            GROUP BY p.order_id, p.[Payment Method], p.[Bank Issuer]
        ),
        line AS (
            SELECT
                d.order_id,
                d.[Group Brand],
                d.[Brand],
                d.[MD Category],
                SUM(d.[Total Sales Incl. PPn]) AS Brand_Sales,
                SUM(d.[Quantity])              AS Qty
            FROM dbo.vw_POSOrderLine d
            WHERE EXISTS (SELECT 1 FROM ord WHERE ord.order_id = d.order_id)
            GROUP BY d.order_id, d.[Group Brand], d.[Brand], d.[MD Category]
        ),
        line_tot AS (
            SELECT order_id, SUM(Brand_Sales) AS Order_Sales
            FROM line GROUP BY order_id
        ),
        gabung AS (
            SELECT
                MONTH(ord.OrderDate)                    AS bulan_no,
                DATENAME(MONTH, ord.OrderDate)          AS bulan,
                ISNULL(pay.[Bank Issuer],    '(blank)') AS bank,
                ISNULL(pay.[Payment Method], '(blank)') AS metode,
                ISNULL(ord.[Branch],         '(blank)') AS branch,
                ISNULL(l.[Group Brand],  'TANPA ITEM')  AS group_brand,
                ISNULL(l.[Brand],        'TANPA ITEM')  AS brand,
                ISNULL(l.[MD Category],  'TANPA ITEM')  AS md_category,
                pay.order_id,
                CASE
                    WHEN l.order_id IS NULL THEN pay.Payment_Amount
                    ELSE pay.Payment_Amount * l.Brand_Sales / lt.Order_Sales
                END                                     AS nilai,
                ISNULL(l.Qty, 0)                        AS qty
            FROM      pay
            JOIN      ord      ON ord.order_id = pay.order_id
            LEFT JOIN line_tot lt ON lt.order_id = pay.order_id
            LEFT JOIN line     l  ON l.order_id  = pay.order_id
                                 AND lt.Order_Sales <> 0
        ),
        bobot AS (
            SELECT order_id, COUNT(*) AS n FROM gabung GROUP BY order_id
        )
        SELECT
            g.bulan_no, g.bulan, g.bank, g.metode, g.branch,
            g.group_brand, g.brand, g.md_category,
            SUM(g.nilai)                            AS nilai,
            SUM(g.qty)                              AS qty,
            SUM(CAST(1.0 AS decimal(19,9)) / b.n)   AS trx
        FROM gabung g
        JOIN bobot  b ON b.order_id = g.order_id
        GROUP BY
            g.bulan_no, g.bulan, g.bank, g.metode, g.branch,
            g.group_brand, g.brand, g.md_category
        """;

    private static final RowMapper<FaktaBrand> MAP_BRAND = (rs, i) -> new FaktaBrand(
            rs.getInt("bulan_no"),
            rs.getString("bulan"),
            rs.getString("bank"),
            rs.getString("metode"),
            rs.getString("branch"),
            rs.getString("group_brand"),
            rs.getString("brand"),
            rs.getString("md_category"),
            rs.getBigDecimal("nilai"),
            rs.getBigDecimal("qty"),
            rs.getBigDecimal("trx"));

    public List<FaktaBrand> ambilBrand(int tahun) {
        return jdbc.query(SQL_BRAND, new MapSqlParameterSource()
                .addValue("dtFrom", LocalDate.of(tahun, 1, 1))
                .addValue("dtTo",   LocalDate.of(tahun + 1, 1, 1)), MAP_BRAND);
    }

    public List<Integer> daftarTahun() {
        return jdbc.getJdbcTemplate().queryForList("""
            SELECT DISTINCT YEAR([Order Date])
            FROM dbo.vw_POSOrder
            WHERE [Order Date] IS NOT NULL
            ORDER BY 1 DESC
            """, Integer.class);
    }
}

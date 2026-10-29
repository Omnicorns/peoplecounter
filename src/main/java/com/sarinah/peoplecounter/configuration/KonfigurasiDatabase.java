package com.sarinah.peoplecounter.configuration;



import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * TIGA SUMBER DATA untuk aplikasi peoplecounter.
 *
 *   utama      spring.datasource.*      database peoplecounter yang sudah ada.
 *                                       Ditandai @Primary, jadi semua kode lama
 *                                       tetap dapat koneksi ini tanpa diubah.
 *
 *   sqlserver  sqlserver.datasource.*   SARINAHDB -- view POS untuk dashboard.
 *                                       DI SINILAH DashboardRepository MEMBACA.
 *
 *   osticket   osticket.datasource.*    MySQL osTicket -- tujuan penyalinan
 *                                       master vendor/brand/margin dari Odoo.
 *
 * KENAPA SEMUANYA HARUS DIDEFINISIKAN DI SINI
 * Begitu ada satu bean DataSource buatan sendiri, Spring Boot berhenti membuat
 * DataSource otomatis dari spring.datasource. Kalau yang didefinisikan cuma
 * yang tambahan, koneksi peoplecounter yang lama justru hilang. Karena itu
 * semuanya ditulis, dan yang lama diberi @Primary supaya tetap jadi default.
 *
 * KENAPA DASHBOARD TIDAK BOLEH IKUT @Primary
 * Semua query DashboardRepository ditulis dalam T-SQL: ISNULL, DATENAME,
 * OBJECT_ID, SELECT TOP, dan nama kolom berkurung siku seperti [Order Date].
 * Database lain menolak hampir semuanya.
 *
 * KALAU SQL SERVER BELUM DINYALAKAN
 * Selama sqlserver.datasource.enabled masih false, bean jdbcSqlServer diarahkan
 * ke koneksi utama. Aplikasi tetap start, tidak ada NoSuchBeanDefinitionException.
 */
@Configuration
public class KonfigurasiDatabase {

    private static final Logger log = LoggerFactory.getLogger(KonfigurasiDatabase.class);

    /* =====================================================================
       1. SUMBER UTAMA -- database peoplecounter yang sudah ada
       ===================================================================== */

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties propUtama() {
        return new DataSourceProperties();
    }

    @Bean(name = "dsUtama")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dsUtama(@Qualifier("propUtama") DataSourceProperties prop) {
        return prop.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "jdbcUtama")
    @Primary
    public NamedParameterJdbcTemplate jdbcUtama(@Qualifier("dsUtama") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    /* =====================================================================
       2. SQL SERVER -- SARINAHDB, sumber angka dashboard
       Bean dsSqlServer hanya dibuat kalau sqlserver.datasource.enabled=true.
       Dipakai saklar tersendiri, bukan "url terisi atau tidak", supaya baris
       url yang kebetulan tertinggal kosong tidak membuat aplikasi gagal start.
       ===================================================================== */

    @Bean
    @ConfigurationProperties("sqlserver.datasource")
    public DataSourceProperties propSqlServer() {
        return new DataSourceProperties();
    }

    @Bean(name = "dsSqlServer")
    @ConditionalOnProperty(name = "sqlserver.datasource.enabled", havingValue = "true")
    public DataSource dsSqlServer(
            @Qualifier("propSqlServer") DataSourceProperties prop,
            @Value("${sqlserver.datasource.hikari.maximum-pool-size:5}") int ukuranPool,
            @Value("${sqlserver.datasource.hikari.connection-timeout:30000}") long tenggang) {

        HikariDataSource ds = prop.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("sqlserver");
        ds.setMaximumPoolSize(ukuranPool);
        ds.setConnectionTimeout(tenggang);
        log.info("Koneksi SQL Server dashboard: {}", prop.getUrl());
        return ds;
    }

    /** Inilah bean yang dicari DashboardRepository. Selalu ada. */
    @Bean(name = "jdbcSqlServer")
    public NamedParameterJdbcTemplate jdbcSqlServer(
            @Qualifier("dsUtama")     DataSource utama,
            @Qualifier("dsSqlServer") ObjectProvider<DataSource> sqlserver) {

        DataSource dipakai = sqlserver.getIfAvailable();
        if (dipakai == null) {
            log.warn("sqlserver.datasource.enabled=false -- dashboard memakai koneksi utama. "
                    + "Query dashboard ditulis dalam T-SQL, jadi ini hanya benar apabila "
                    + "spring.datasource memang mengarah ke SQL Server.");
            dipakai = utama;
        }
        return new NamedParameterJdbcTemplate(dipakai);
    }

    /* =====================================================================
       3. MYSQL osTicket -- tujuan salinan master vendor/brand/margin

       BEDA SIKAP DENGAN SQL SERVER DI ATAS
       jdbcSqlServer sengaja selalu ada dan jatuh ke koneksi utama kalau belum
       dinyalakan, karena dashboard adalah inti aplikasi dan harus tetap start.
       Yang ini kebalikannya: KALAU MATI, BEAN-NYA MEMANG TIDAK DIBUAT.

       Alasannya, jatuh ke koneksi utama di sini justru berbahaya. Perintah
       penyalinan ditulis dalam dialek MySQL -- RENAME TABLE tiga arah, backtick,
       INSERT ... ON DUPLICATE KEY UPDATE. Kalau diarahkan diam-diam ke
       PostgreSQL peoplecounter, yang terjadi bukan error yang jelas, melainkan
       perintah yang menyentuh database yang salah.

       Konsekuensinya: komponen yang menyuntik jdbcOsTicket HARUS ikut memakai
           @ConditionalOnProperty(name = "osticket.datasource.enabled",
                                  havingValue = "true")
       supaya aplikasi tetap start waktu saklarnya masih mati.
       ===================================================================== */

    @Bean
    @ConfigurationProperties("osticket.datasource")
    public DataSourceProperties propOsTicket() {
        return new DataSourceProperties();
    }

    @Bean(name = "dsOsTicket")
    @ConditionalOnProperty(name = "osticket.datasource.enabled", havingValue = "true")
    public DataSource dsOsTicket(
            @Qualifier("propOsTicket") DataSourceProperties prop,
            @Value("${osticket.datasource.hikari.maximum-pool-size:3}") int ukuranPool,
            @Value("${osticket.datasource.hikari.connection-timeout:30000}") long tenggang) {

        HikariDataSource ds = prop.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("osticket");
        /* Pool kecil saja. Penyalinan jalan sehari sekali dari satu utas;
           kolam besar cuma menahan koneksi ke MySQL osTicket sepanjang hari
           tanpa dipakai, dan osTicket sendiri butuh koneksi itu untuk melayani
           orang yang sedang membuka tiket. */
        ds.setMaximumPoolSize(ukuranPool);
        ds.setConnectionTimeout(tenggang);
        /* Koneksi yang menganggur lama di MySQL sering diputus sepihak oleh
           server (wait_timeout, bawaannya 8 jam). Didaur ulang jauh sebelum itu
           supaya penyalinan besok pagi tidak kena "broken pipe". */
        ds.setMaxLifetime(1_800_000);      // 30 menit
        ds.setIdleTimeout(600_000);        // 10 menit
        ds.setMinimumIdle(0);
        log.info("Koneksi MySQL osTicket: {}", prop.getUrl());
        return ds;
    }

    @Bean(name = "jdbcOsTicket")
    @ConditionalOnProperty(name = "osticket.datasource.enabled", havingValue = "true")
    public NamedParameterJdbcTemplate jdbcOsTicket(@Qualifier("dsOsTicket") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
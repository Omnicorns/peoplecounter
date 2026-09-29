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
 * DUA SUMBER DATA untuk aplikasi peoplecounter.
 *
 *   utama      spring.datasource.*      database peoplecounter yang sudah ada.
 *                                       Ditandai @Primary, jadi semua kode lama
 *                                       tetap dapat koneksi ini tanpa diubah.
 *
 *   sqlserver  sqlserver.datasource.*   SARINAHDB -- view POS untuk dashboard.
 *                                       DI SINILAH DashboardRepository MEMBACA.
 *
 * KENAPA KEDUANYA HARUS DIDEFINISIKAN DI SINI
 * Begitu ada satu bean DataSource buatan sendiri, Spring Boot berhenti membuat
 * DataSource otomatis dari spring.datasource. Kalau yang didefinisikan cuma
 * yang SQL Server, koneksi peoplecounter yang lama justru hilang. Karena itu
 * dua-duanya ditulis, dan yang lama diberi @Primary supaya tetap jadi default.
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
}
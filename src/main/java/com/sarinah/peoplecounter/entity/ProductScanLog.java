package com.sarinah.peoplecounter.entity;

import jakarta.persistence.*;

import java.time.Instant;



import jakarta.persistence.*;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;


@Entity
@Table(name = "product_scan_log",
        indexes = {
                @Index(name = "idx_ts", columnList = "ts"),
                @Index(name = "idx_username_ts", columnList = "username, ts"),
                @Index(name = "idx_source_ts", columnList = "source, ts")
        })
@Setter
@Getter
public class ProductScanLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant ts;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "value", nullable = false, length = 128)
    private String value;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanSource source = ScanSource.WEB; // default

    public ProductScanLog() {}

    public ProductScanLog(String username, String value, String productName, ScanSource source) {
        this.username = username;
        this.value = value;
        this.productName = productName;
        this.source = (source != null) ? source : ScanSource.WEB;
    }

    // getters/setters …
}

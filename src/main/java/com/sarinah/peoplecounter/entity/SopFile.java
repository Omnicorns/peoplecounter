package com.sarinah.peoplecounter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import org.hibernate.type.SqlTypes;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "sop_files",
        indexes = @Index(name = "idx_sop_files_sop", columnList = "sop_id"))
public class SopFile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sop_id")
    private SopDocument sop;

    @Column(name="file_name", nullable=false, length=512)
    private String fileName;

    @Column(length=255) private String mime;
    @Column(nullable=false) private long size;

    // IMPORTANT: jangan pakai @Lob untuk bytea
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name="data", nullable=false)   // columnDefinition tidak wajib kalau sudah bytea
    private byte[] data;
}
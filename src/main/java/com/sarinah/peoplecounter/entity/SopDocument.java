package com.sarinah.peoplecounter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "sop_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_sop_document_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_sop_document_status", columnList = "status"),
                @Index(name = "idx_sop_document_title",  columnList = "title"),
                @Index(name = "idx_sop_document_code",   columnList = "code")
        })
public class SopDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    private String category;     // Operasional/HR/Legal/IT
    private String status;       // Draft/Review/Published/Archived
    private String owner;
    private String version = "1.0";
    private LocalDate effective;

    @Column(name = "change_summary", columnDefinition = "text")
    private String changeSummary;

    // Tag sederhana
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sop_tags",
            joinColumns = @JoinColumn(name = "sop_id",
                    foreignKey = @ForeignKey(name = "fk_sop_tags_sop")))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new LinkedHashSet<>();
}
package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.MiddlewareUser;
import com.sarinah.peoplecounter.entity.PdfDocs;
import com.sarinah.peoplecounter.entity.PeopleCount;
import com.sarinah.peoplecounter.projection.PdfDocSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PdfDocRepository  extends JpaRepository<PdfDocs,Long> {
    @Query("SELECT p.id AS id, p.filename AS filename, p.contentType AS contentType " +
            "FROM PdfDocs p WHERE LOWER(p.filename) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<PdfDocSummary> searchByFilename(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p.id AS id, p.filename AS filename, p.contentType AS contentType FROM PdfDocs p")
    Page<PdfDocSummary> findAllSummary(Pageable pageable);

    Page<PdfDocs> findByFilenameContainingIgnoreCase(String q, Pageable pageable);
}

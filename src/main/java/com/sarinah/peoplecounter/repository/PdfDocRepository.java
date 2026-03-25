package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.MiddlewareUser;
import com.sarinah.peoplecounter.entity.PdfDocs;
import com.sarinah.peoplecounter.entity.PeopleCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfDocRepository  extends JpaRepository<PdfDocs,Long> {
    Page<PdfDocs> findByFilenameContainingIgnoreCase(String q, Pageable pageable);
}

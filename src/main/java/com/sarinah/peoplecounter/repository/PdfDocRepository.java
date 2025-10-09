package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.MiddlewareUser;
import com.sarinah.peoplecounter.entity.PdfDocs;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfDocRepository  extends JpaRepository<PdfDocs,Long> {
}

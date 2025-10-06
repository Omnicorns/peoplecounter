package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.ProductScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductScanLogRepository extends JpaRepository<ProductScanLog, Long> {
}

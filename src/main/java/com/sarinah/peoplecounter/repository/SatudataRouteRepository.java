package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.ProductScanLog;
import com.sarinah.peoplecounter.entity.SatudataRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SatudataRouteRepository extends JpaRepository<SatudataRoute, Long> {
    Optional<SatudataRoute> findByPathKeyAndIsActiveTrue(String pathKey);

    List<SatudataRoute>findAllActiveByPathKey(String pathKey);
}
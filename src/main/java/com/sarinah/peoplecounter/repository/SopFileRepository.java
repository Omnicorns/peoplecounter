package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.SopFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SopFileRepository extends JpaRepository<SopFile, Long> {
    List<SopFile> findBySopId(Long sopId);
}

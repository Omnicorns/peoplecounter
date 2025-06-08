package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.PeopleCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PeopleCountRepository extends JpaRepository<PeopleCount,Long> {
    Page<PeopleCount> findByNameContainingIgnoreCase(String name, Pageable pageable);
}

package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.PeopleCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


import java.util.Date;
import java.util.Optional;

@Repository
public interface PeopleCountRepository extends JpaRepository<PeopleCount,Long> {
    Page<PeopleCount> findByNameContainingIgnoreCase(String name, Pageable pageable);


    Optional<PeopleCount> findByCountDateAndName(Date countDate, String name);

    @Query(
            value = """
        SELECT * FROM people_count
        WHERE DATE(count_date) BETWEEN :startDate AND :endDate
        AND (
              :inboundFlag = false
               OR date_inbound >= CURRENT_DATE
               )
        ORDER BY count_date DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM people_count
        WHERE DATE(count_date) BETWEEN :startDate AND :endDate
         AND (
              :inboundFlag = false
               OR date_inbound >= CURRENT_DATE
              )
        """,
            nativeQuery = true
    )
    Page<PeopleCount> findByCountDateBetweenNative(
             Date startDate,
             Date endDate,
             boolean inboundFlag,
            Pageable pageable
    );

    @Query(
            value = """
        SELECT * FROM people_count
        WHERE LOWER(name) LIKE LOWER(CONCAT('%', :name, '%'))
          AND DATE(count_date) BETWEEN :startDate AND :endDate
        ORDER BY count_date DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM people_count
        WHERE LOWER(name) LIKE LOWER(CONCAT('%', :name, '%'))
          AND DATE(count_date) BETWEEN :startDate AND :endDate
        """,
            nativeQuery = true
    )
    Page<PeopleCount> findByNameAndDateNative(
            String name,
            Date startDate,
            Date endDate,
            Pageable pageable
    );

    @Query(
            value = """
        SELECT * FROM people_count
        WHERE DATE(count_date) BETWEEN :startDate AND :endDate
          AND date_inbound BETWEEN :inboundStart AND :inboundEnd
        ORDER BY count_date DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM people_count
        WHERE DATE(count_date) BETWEEN :startDate AND :endDate
          AND date_inbound BETWEEN :inboundStart AND :inboundEnd
        """,
            nativeQuery = true
    )
    Page<PeopleCount> findByCountDateAndInboundDateBetweenNative(
            Date startDate,
            Date endDate,
            Date inboundStart,
            Date inboundEnd,
            Pageable pageable
    );


}

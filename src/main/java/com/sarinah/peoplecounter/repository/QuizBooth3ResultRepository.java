package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.QuizBooth3Result;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizBooth3ResultRepository extends JpaRepository<QuizBooth3Result, Long> {

    List<QuizBooth3Result> findAllByOrderByScoreDescDurationSecondsAscCreatedAtAsc(Pageable pageable);

    @Query("""
        SELECT COUNT(r) > 0
        FROM QuizBooth3Result r
        WHERE LOWER(TRIM(r.name)) = LOWER(TRIM(:name))
    """)
    boolean existsByNormalizedName(@Param("name") String name);
}

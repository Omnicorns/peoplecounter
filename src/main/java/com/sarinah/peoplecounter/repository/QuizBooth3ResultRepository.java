package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.QuizBooth3Result;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizBooth3ResultRepository extends JpaRepository<QuizBooth3Result, Long> {

    List<QuizBooth3Result> findAllByOrderByScoreDescDurationSecondsAscCreatedAtAsc(Pageable pageable);
}

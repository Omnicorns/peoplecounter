package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.SopDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SopDocumentRepository extends JpaRepository<SopDocument, Long> {
    @Query("""
     select s from SopDocument s
     where (:qLike is null or
            lower(concat(coalesce(s.code,''),' ',coalesce(s.title,''))) like :qLike)
       and (:statusLower is null or
            lower(coalesce(s.status,'')) = :statusLower)
     order by s.id desc
  """)
    List<SopDocument> search(@Param("qLike") String qLike,
                             @Param("statusLower") String statusLower);

    Optional<SopDocument> findByCode(String code);

}

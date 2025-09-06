package com.sarinah.peoplecounter.repository;

import com.sarinah.peoplecounter.entity.MiddlewareUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MiddlewareUserRepository extends JpaRepository<MiddlewareUser,Long> {
    Optional<MiddlewareUser> findByUsername(String username);
    boolean existsByUsername(String username);
}

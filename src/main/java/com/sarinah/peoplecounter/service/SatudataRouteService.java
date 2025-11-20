package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.SatudataRoute;
import com.sarinah.peoplecounter.repository.SatudataRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SatudataRouteService {
    private final SatudataRouteRepository repository;




    public Optional<SatudataRoute> findActiveByPathKey(String pathKey) {
        return repository.findByPathKeyAndIsActiveTrue(pathKey);
    }
}

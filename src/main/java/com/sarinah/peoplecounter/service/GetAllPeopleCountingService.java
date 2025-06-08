package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.PeopleCount;
import com.sarinah.peoplecounter.repository.PeopleCountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class GetAllPeopleCountingService {
    @Autowired
    PeopleCountRepository peopleCountRepository;

    public GetAllPeopleCountingService(PeopleCountRepository peopleCountRepository){
        this.peopleCountRepository = peopleCountRepository;
    }

    public Page<PeopleCount> getPaginated(int pageIndex, int pageSize) {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        return peopleCountRepository.findAll(pageable);
    }

    public Page<PeopleCount> searchByName(String name, int pageIndex, int pageSize) {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        return peopleCountRepository.findByNameContainingIgnoreCase(name, pageable);
    }

}

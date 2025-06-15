package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.PeopleCount;
import com.sarinah.peoplecounter.repository.PeopleCountRepository;
import com.sarinah.peoplecounter.response.PeopleCountResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class GetAllPeopleCountingService {
    @Autowired
    PeopleCountRepository peopleCountRepository;

    @Autowired
    MapPeopleCountingResponseService mapPeopleCountingResponseService;

    public GetAllPeopleCountingService(PeopleCountRepository peopleCountRepository, MapPeopleCountingResponseService mapPeopleCountingResponseService){
        this.peopleCountRepository = peopleCountRepository;
        this.mapPeopleCountingResponseService = mapPeopleCountingResponseService;
    }



    public Page<PeopleCountResponse> search (String name, Date startDate, Date endDate, int pageIndex, int pageSize) {
        Pageable pageReq = PageRequest.of(pageIndex, pageSize);


        boolean hasName  = name      != null && !name.isBlank();
        boolean hasDates = startDate != null && endDate != null;

        Page<PeopleCount> result;
        if (hasName && hasDates) {
            // nama + tanggal
            result = peopleCountRepository.findByNameAndDateNative(
                    name, startDate, endDate, pageReq);
        }
        else if (hasDates) {
            // hanya tanggal
            result = peopleCountRepository.findByCountDateBetweenNative(startDate, endDate, pageReq);
        }
        else if (hasName) {
            // hanya nama
            result = peopleCountRepository.findByNameContainingIgnoreCase(name, pageReq);
        }
        else {
            // paging tanpa filter
            result = peopleCountRepository.findAll(pageReq);
        }

        return result.map(MapPeopleCountingResponseService::fromEntity);
    }

}

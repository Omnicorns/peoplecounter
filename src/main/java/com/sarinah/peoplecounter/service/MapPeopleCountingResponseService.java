package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.PeopleCount;
import com.sarinah.peoplecounter.response.PeopleCountResponse;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;

@Service
public class MapPeopleCountingResponseService {

    public static PeopleCountResponse fromEntity(PeopleCount entity) {
        return PeopleCountResponse.builder()
                .day(entity.getDay())
                .date(new SimpleDateFormat("dd/MM/yyyy").format(entity.getCountDate()))
                .name(entity.getName())
                .in(entity.getInCount()
                        .stripTrailingZeros()
                        .toPlainString())
                .out(entity.getOutCount()
                        .stripTrailingZeros()
                        .toPlainString())
                .avg(entity.getAvgCount()
                        .stripTrailingZeros()
                        .toPlainString())
                .build();
    }
}

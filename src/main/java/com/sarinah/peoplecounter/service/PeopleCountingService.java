package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.PeopleCount;
import com.sarinah.peoplecounter.repository.PeopleCountRepository;
import com.sarinah.peoplecounter.request.PeopleCountRequest;
import com.sarinah.peoplecounter.response.PeopleCountResponse;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;


@Service
public class PeopleCountingService {
    @Autowired
    PeopleCountRepository peopleCountRepository;

    public PeopleCountingService(PeopleCountRepository peopleCountRepository){
        this.peopleCountRepository = peopleCountRepository;
    }
    @SneakyThrows
    public PeopleCountResponse execute(PeopleCountRequest input){
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date countDate = sdf.parse(input.getDate());
        BigDecimal avg = new BigDecimal(input.getAvg());
        java.util.Date utilDate = input.getDateInbound();
        java.sql.Timestamp dateInbound = new java.sql.Timestamp(utilDate.getTime());
        PeopleCount peopleCount = new PeopleCount();
        peopleCount.setDay(input.getDay());
        peopleCount.setName(input.getName());
        peopleCount.setInCount(new BigDecimal(input.getIn()));
        peopleCount.setCountDate(countDate);
        peopleCount.setOutCount(new BigDecimal(input.getOut()));
        peopleCount.setDateInbound(dateInbound);
        peopleCount.setAvgCount(avg);
        peopleCount.setFilename(input.getFilename());
        peopleCountRepository.save(peopleCount);
        return  new PeopleCountResponse();


    }


}

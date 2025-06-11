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
import java.time.LocalDate;
import java.time.ZoneId;
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
        LocalDate localDate = LocalDate.parse(input.getDate());        // ISO yyyy-MM-dd
        Date countDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        BigDecimal avg = new BigDecimal(input.getAvg());
        BigDecimal inCount = new BigDecimal(input.getIn());
        BigDecimal outCount = new BigDecimal(input.getOut());

        java.util.Date utilDate = input.getDateInbound();
        Timestamp dateInbound = new Timestamp(utilDate.getTime());

        PeopleCount peopleCount = new PeopleCount();
        peopleCount.setDay(input.getDay());
        peopleCount.setName(input.getName());
        peopleCount.setCountDate(countDate);
        peopleCount.setInCount(inCount);
        peopleCount.setOutCount(outCount);
        peopleCount.setDateInbound(dateInbound);
        peopleCount.setAvgCount(avg);
        peopleCount.setFilename(input.getFilename());

        peopleCountRepository.save(peopleCount);
        return  new PeopleCountResponse();


    }


}

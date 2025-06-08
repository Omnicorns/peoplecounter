package com.sarinah.peoplecounter.route.processor;

import com.sarinah.peoplecounter.request.PeopleCountRequest;
import com.sarinah.peoplecounter.service.PeopleCountingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class AddTaskFileProcessor implements Processor {
    @Autowired
    PeopleCountingService peopleCountingService;
    @Override
    public void process(Exchange exchange) throws Exception {
        String filename = exchange.getIn().getHeader(Exchange.FILE_NAME).toString();
        String message = exchange.getMessage().getBody(String.class);
        String[] tokenLines = message.split("\\r?\\n");
        List<String> tokenLinesAsList = Arrays.asList(tokenLines);
        List<List<String>> tokenLinesBatch = ListUtils.partition(tokenLinesAsList, 10000);
        for (List<String> batches : tokenLinesBatch) {
            batches.forEach(tokenLine -> {
                String[] tokenLineColumns = tokenLine.split("\\|");

                PeopleCountRequest peopleCountRequest = new PeopleCountRequest();
                peopleCountRequest.setDay(tokenLineColumns[0].trim());
                peopleCountRequest.setDate(tokenLineColumns[1].trim());
                peopleCountRequest.setName(tokenLineColumns[2].trim());
                peopleCountRequest.setIn(tokenLineColumns[3].trim());
                peopleCountRequest.setOut(tokenLineColumns[4].trim());
                peopleCountRequest.setAvg(tokenLineColumns[5].trim());
                peopleCountRequest.setDateInbound(new Date());
                peopleCountRequest.setFilename(filename);
                peopleCountingService.execute(peopleCountRequest);


            });
        }
    }


    }


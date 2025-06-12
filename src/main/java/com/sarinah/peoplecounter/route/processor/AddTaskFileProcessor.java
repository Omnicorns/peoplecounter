package com.sarinah.peoplecounter.route.processor;

import com.sarinah.peoplecounter.request.PeopleCountRequest;
import com.sarinah.peoplecounter.service.PeopleCountingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;


import static java.util.stream.Collectors.toList;

@Component
@Slf4j
public class AddTaskFileProcessor implements Processor {
    @Autowired
    PeopleCountingService peopleCountingService;

    private static final Pattern DATA_LINE = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{2}\\b.*");

    // ukuran batch per partition
    private static final int BATCH_SIZE = 10_000;

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final Map<String,DayOfWeek> IND_DAY_MAP = Map.of(
            "Sen", DayOfWeek.MONDAY,
            "Sel", DayOfWeek.TUESDAY,
            "Rab", DayOfWeek.WEDNESDAY,
            "Kam", DayOfWeek.THURSDAY,
            "Jum", DayOfWeek.FRIDAY,
            "Sab", DayOfWeek.SATURDAY,
            "Min", DayOfWeek.SUNDAY
    );



    @Override
    public void process(Exchange exchange) throws Exception {
        String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        String body     = exchange.getMessage().getBody(String.class);


        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Empty file: " + filename);
        }

        List<String> dataLines = Arrays.stream(body.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(toList());

        if (dataLines.isEmpty()) {
            throw new IllegalArgumentException("No data in file: " + filename);
        }
        List<String> badLines =dataLines.stream()
                .filter(line -> !DATA_LINE.matcher(line).matches())
                .toList();

        if (!badLines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Malformed lines in " + filename
            );
        }


        List<List<String>> batches = ListUtils.partition(dataLines, BATCH_SIZE);

        for (List<String> batch : batches) {
            log.info("Processing batch of {} lines from file {}", batch.size(), filename);
            for (String line : batch) {
                try {
                    String[] cols = line.split(";");
                    if (cols.length < 6) { /* skip malformed */ }


                    String[] parts = cols[0].trim().split("\\s+", 2);
                    String datePart = parts[0];           // "02.06.25"
                    String dayAbbr  = parts.length>1?parts[1]:"";


                    LocalDate countDate = LocalDate.parse(datePart, DTF);


                    DayOfWeek dow = countDate.getDayOfWeek();
                    if (IND_DAY_MAP.containsKey(dayAbbr)
                            && IND_DAY_MAP.get(dayAbbr) != dow) {
                        log.warn("Day‐of‐week mismatch: '{}' != {}", dayAbbr, dow);
                    }

                    String fullDayName = dow.getDisplayName(TextStyle.FULL, new Locale("id"));

                    // 4) isi request
                    PeopleCountRequest req = new PeopleCountRequest();
                    req.setDay(fullDayName);                  // simpan "Sen"
                    req.setDate(countDate.toString());    // simpan "2025-06-02"
                    req.setName(cols[1].trim());
                    req.setIn(cols[2].trim());
                    req.setOut(cols[3].trim());
                    req.setAvg(cols[4].trim());
                    req.setDateInbound(new Date());
                    req.setFilename(filename);

                    peopleCountingService.execute(req);
                } catch (Exception e) {
                    log.error("Error processing data line in {}: {}", filename, line, e);
                }
            }
        }
    }


}


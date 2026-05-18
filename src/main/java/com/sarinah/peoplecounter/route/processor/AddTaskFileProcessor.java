package com.sarinah.peoplecounter.route.processor;

import com.sarinah.peoplecounter.entity.PeopleCount;
import com.sarinah.peoplecounter.repository.PeopleCountRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;


@Component
@Slf4j
public class AddTaskFileProcessor implements Processor {

    @Autowired
    PeopleCountRepository peopleCountRepository;

    private static final Pattern DATA_LINE = Pattern.compile("^[^;]+;[^;]+;\\d{4}-\\d{2}-\\d{2}_\\d{2}:\\d{2};.*");



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
                .filter(line -> DATA_LINE.matcher(line).matches())
                .toList();


        if (dataLines.isEmpty()) {
            throw new IllegalArgumentException("No data in file: " + filename);
        }
        Map<String, IntSummaryStatistics> inStats = new HashMap<>();
        Map<String, IntSummaryStatistics> outStats = new HashMap<>();
        Map<String, IntSummaryStatistics> avgStats = new HashMap<>();

        for (String line : dataLines) {
            try {
                String[] cols = line.split(";");
                if (cols.length < 7) {
                    log.warn("Skip malformed line: {}", line);
                    continue;
                }

                String location = cols[1].trim(); // SARINAH BLDNG
                String dateTimeRaw = cols[2].trim(); // 2025-06-13_00:10
                String[] dateTimeParts = dateTimeRaw.split("_");
                if (dateTimeParts.length < 1) continue;

                String dateOnly = dateTimeParts[0]; // "2025-06-13"
                LocalDate countDate = LocalDate.parse(dateOnly);

                int in = safeParseInt(cols[3]);
                int out = safeParseInt(cols[4]);
                int avg = (in + out) / 2;

                String key = location + "#" + countDate;

                inStats.computeIfAbsent(key, k -> new IntSummaryStatistics()).accept(in);
                outStats.computeIfAbsent(key, k -> new IntSummaryStatistics()).accept(out);
                avgStats.computeIfAbsent(key, k -> new IntSummaryStatistics()).accept(avg);

            } catch (Exception e) {
                log.error("Error parsing line '{}': {}", line, e.getMessage());
            }
        }

        for (String key : inStats.keySet()) {
            String[] parts = key.split("#");
            String location = parts[0];
            location = location.replace("BLDNG", "BUILDING");
            LocalDate date = LocalDate.parse(parts[1]);
            Date javaDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());

            String fullDay = date.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("id"));

            BigDecimal totalIn = BigDecimal.valueOf(inStats.get(key).getSum());
            BigDecimal totalOut = BigDecimal.valueOf(outStats.get(key).getSum());
            BigDecimal totalAvg = BigDecimal.valueOf(avgStats.get(key).getSum());

            Optional<PeopleCount> existing = peopleCountRepository.findByCountDateAndName(javaDate, location);

            if (existing.isPresent()) {
                PeopleCount entity = existing.get();

                if (entity.getInCount().compareTo(totalIn) >= 0
                        && entity.getOutCount().compareTo(totalOut) >= 0
                        && entity.getAvgCount().compareTo(totalAvg) >= 0) {
                    log.info("⏭️  SKIPPED: {} - {} (existing in={}, out={}, avg={} >= new in={}, out={}, avg={})",
                            date, location,
                            entity.getInCount(), entity.getOutCount(), entity.getAvgCount(),
                            totalIn, totalOut, totalAvg);
                    return; // atau continue; tergantung ini di dalam loop atau method
                }

                entity.setInCount(totalIn);
                entity.setOutCount(totalOut);
                entity.setAvgCount(totalAvg);
                entity.setDay(fullDay);
                entity.setDateInbound(new Timestamp(System.currentTimeMillis()));

                entity.setFilename(filename);
                peopleCountRepository.save(entity);
                log.info("✅ UPDATED: {} - {} (in={}, out={}, avg={})", date, location, totalIn, totalOut, totalAvg);
            } else {
                // ➕ INSERT
                PeopleCount entity = new PeopleCount();
                entity.setCountDate(javaDate);
                entity.setName(location);
                entity.setDay(fullDay);
                entity.setInCount(totalIn);
                entity.setOutCount(totalOut);
                entity.setAvgCount(totalAvg);
                entity.setDateInbound(new Timestamp(System.currentTimeMillis()));
                entity.setFilename(filename);
                peopleCountRepository.save(entity);
                log.info("✅ INSERTED: {} - {} (in={}, out={}, avg={})", date, location, totalIn, totalOut, totalAvg);
            }

            }
        }



    private int safeParseInt(String val) {
        if (val == null) return 0;
        val = val.trim();
        if (val.equals("-") || val.isEmpty()) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }


}


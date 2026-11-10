package com.sarinah.peoplecounter.route;

import com.sarinah.peoplecounter.route.processor.AddTaskFileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Pengganti route Apache Camel "add-file-task-route" (Camel belum mendukung Spring Boot 4).
 *
 * Perilaku sama dengan route lama:
 *  - memantau folder inbound (tidak rekursif),
 *  - hanya menerima file .csv,
 *  - menyalin file ke folder outbound, lalu memproses isinya,
 *  - sukses  -> dipindah ke  inbound/processed/yyyyMMdd/nama-file,
 *  - gagal   -> dipindah ke  error/yyyyMMdd/nama-file dan error dicatat di log.
 */
@Component
@Slf4j
public class InboundFilePoller {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AddTaskFileProcessor processor;
    private final Path inbound;
    private final Path outbound;
    private final Path errorDir;
    private final long minAgeMillis;

    public InboundFilePoller(
            AddTaskFileProcessor processor,
            @Value("${inbound.dir:/mnt/inbound}") String inboundDir,
            @Value("${inbound.outbound-dir:/mnt/outbound}") String outboundDir,
            @Value("${inbound.error-dir:/mnt/error}") String errorDir,
            // file yang baru saja diubah dianggap masih ditulis oleh pengirim
            @Value("${inbound.min-age-ms:2000}") long minAgeMillis) {
        this.processor = processor;
        this.inbound = Path.of(inboundDir);
        this.outbound = Path.of(outboundDir);
        this.errorDir = Path.of(errorDir);
        this.minAgeMillis = minAgeMillis;
    }

    @Scheduled(fixedDelayString = "${inbound.poll-delay-ms:1000}")
    public void poll() {
        if (!Files.isDirectory(inbound)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(inbound)) {
            files = list
                    .filter(Files::isRegularFile)
                    .filter(f -> !f.getFileName().toString().startsWith("."))
                    .filter(this::isStable)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Gagal membaca folder inbound {}: {}", inbound, e.getMessage());
            return;
        }
        for (Path file : files) {
            handle(file);
        }
    }

    private boolean isStable(Path file) {
        try {
            return System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() >= minAgeMillis;
        } catch (IOException e) {
            return false;
        }
    }

    private void handle(Path file) {
        String name = file.getFileName().toString();
        String day = LocalDate.now().format(DAY);
        log.info("Menerima file: {}", name);
        try {
            if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                throw new IllegalArgumentException("File bukan CSV!");
            }
            log.info("File valid, memproses: {}", name);

            byte[] content = Files.readAllBytes(file);
            Files.createDirectories(outbound);
            Files.write(outbound.resolve(name), content);

            processor.process(name, new String(content, StandardCharsets.UTF_8));

            moveTo(file, inbound.resolve("processed").resolve(day).resolve(name));
            log.info("File berhasil diproses: {}", name);
        } catch (Exception e) {
            log.error(">> ERROR saat memproses file {} pada {}, error: {}", name, java.time.LocalDateTime.now(), e.getMessage());
            try {
                moveTo(file, errorDir.resolve(day).resolve(name));
            } catch (IOException io) {
                log.error("Gagal memindahkan file {} ke folder error: {}", name, io.getMessage());
            }
        }
    }

    private void moveTo(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}

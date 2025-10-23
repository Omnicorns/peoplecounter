package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.entity.PdfDocs;
import com.sarinah.peoplecounter.repository.PdfDocRepository;
import com.sarinah.peoplecounter.service.UserImportService;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class UserImportApi {

    private final UserImportService service;
    private final PdfDocRepository pdfDocRepository;

    // Import CSV (multipart/form-data)
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserImportService.UploadResult importCsv(@RequestPart("file") MultipartFile file) throws Exception {
        return service.importCsv(file.getInputStream());
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File kosong");
        if (!Objects.equals(file.getContentType(), "application/pdf"))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Harus PDF");

        PdfDocs doc = new PdfDocs();
        doc.setFilename(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setData(file.getBytes());
        doc = pdfDocRepository.save(doc);

        return Map.of(
                "id", doc.getId(),
                "filename", doc.getFilename(),
                "url", "/pdf/" + doc.getId(),          // endpoint baca yang sudah kita buat
                "open_in_viewer", "/catalogue?id=" + doc.getId()
        );

    }


    @GetMapping(value="/pdf/{id}", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> stream(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers) {

        // --- Ambil sekali dari DB tiap request (tanpa cache) ---
        PdfDocs doc = pdfDocRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] data = Objects.requireNonNull(doc.getData(), "PDF kosong");
        long len = data.length;

        // ETag sederhana dari isi (tanpa kolom tambahan)
        String etag = "\"" + Integer.toHexString(Arrays.hashCode(data)) + "\"";

        // 304 Not Modified (ETag)
        if (headers.getIfNoneMatch() != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }

        // Header umum
        String filename = (doc.getFilename()!=null && !doc.getFilename().isBlank())
                ? doc.getFilename() : (id + ".pdf");

        HttpHeaders base = new HttpHeaders();
        base.setContentType(MediaType.APPLICATION_PDF);
        base.setContentDisposition(ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8).build());
        base.setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
        base.setETag(etag);
        base.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        // If-Range berbasis ETag (opsional, aman)
        boolean ignoreRange = false;
        String ifRange = headers.getFirst(HttpHeaders.IF_RANGE);
        if (ifRange != null && ifRange.startsWith("\"") && !etag.equals(ifRange)) {
            ignoreRange = true;
        }

        List<HttpRange> ranges = ignoreRange ? Collections.emptyList() : headers.getRange();

        if (ranges == null || ranges.isEmpty()) {
            // Full content 200
            ByteArrayResource body = new ByteArrayResource(data);
            base.setContentLength(len);
            return new ResponseEntity<>(body, base, HttpStatus.OK);
        }

        // Partial content 206 (ambil satu range)
        HttpRange r = ranges.get(0);
        long start = r.getRangeStart(len);
        long end   = r.getRangeEnd(len);

        if (start < 0 || start >= len) {
            return new ResponseEntity<>(base, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (end < 0 || end >= len) end = len - 1;
        if (end < start) {
            return new ResponseEntity<>(base, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }

        long chunk = end - start + 1;
        if (chunk > Integer.MAX_VALUE) { // jaga2 file super besar
            end = start + Integer.MAX_VALUE - 1;
            chunk = end - start + 1;
        }

        InputStreamResource body =
                new InputStreamResource(new ByteArrayInputStream(data, (int) start, (int) chunk));

        HttpHeaders part = new HttpHeaders();
        part.addAll(base);
        part.setContentLength(chunk);
        part.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + len);

        return new ResponseEntity<>(body, part, HttpStatus.PARTIAL_CONTENT);
    }

    // ====== sesuai schema kamu (tanpa ubah tabel) ======
    @PostMapping(value = "/pdf/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadBulk(@RequestPart("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tidak ada file yang diunggah");
        }

        // Batas wajar biar nggak kebablasan (opsional)
        if (files.size() > 100) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maksimum 100 file per unggahan");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        int success = 0, failed = 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String fname = file != null ? file.getOriginalFilename() : null;

            try {
                // Validasi dasar
                if (file == null || file.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File kosong: " + fname);
                }
                // Beberapa browser kadang kirim application/octet-stream — cek magic header %PDF juga
                if (!isPdf(file)) {
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "Bukan PDF: " + fname);
                }

                PdfDocs doc = new PdfDocs();
                doc.setFilename(fname);
                doc.setContentType("application/pdf");
                doc.setData(file.getBytes());

                doc = pdfDocRepository.save(doc);

                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("index", i);
                ok.put("filename", doc.getFilename());
                ok.put("id", doc.getId());
                ok.put("url", "/pdf/" + doc.getId());
                ok.put("open_in_viewer", "/catalogue?id=" + doc.getId());
                ok.put("status", "OK");
                items.add(ok);
                success++;

            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("index", i);
                err.put("filename", fname);
                err.put("status", "ERROR");
                err.put("error", e.getMessage());
                items.add(err);
                failed++;
            }
        }

        return Map.of(
                "total", files.size(),
                "success", success,
                "failed", failed,
                "items", items
        );
    }

    /**
     * Deteksi cepat PDF:
     * - Content-Type 'application/pdf' ATAU
     * - Magic header file diawali "%PDF"
     */
    private boolean isPdf(MultipartFile file) throws IOException {
        String ct = file.getContentType();
        if ("application/pdf".equalsIgnoreCase(ct)) return true;

        byte[] head = file.getInputStream().readNBytes(5);
        return head.length >= 4 &&
                head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
    }


    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> updatePdf(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "filename", required = false) String filename
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File kosong");
        }
        if (!isPdf(file)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Harus PDF");
        }

        PdfDocs doc = pdfDocRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dokumen tidak ditemukan"));

        // Update konten
        doc.setData(file.getBytes());
        doc.setContentType("application/pdf");
        // Pakai filename baru jika dikirim, kalau tidak pakai original filename dari upload
        String newName = (filename != null && !filename.isBlank())
                ? filename
                : (file.getOriginalFilename() != null ? file.getOriginalFilename() : doc.getFilename());
        doc.setFilename(newName);

        doc = pdfDocRepository.save(doc);

        return Map.of(
                "id", doc.getId(),
                "filename", doc.getFilename(),
                "url", "/pdf/" + doc.getId(),
                "open_in_viewer", "/catalogue?id=" + doc.getId(),
                "status", "UPDATED"
        );
    }



}

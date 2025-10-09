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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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


    @GetMapping("/pdf/{id}")
    public ResponseEntity<Resource> streamPdf(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers) {

        PdfDocs doc = pdfDocRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] bytes = doc.getData();
        long length = bytes.length;

        List<HttpRange> ranges = headers.getRange();
        if (ranges == null || ranges.isEmpty()) {
            // full content
            ByteArrayResource res = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(length)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .cacheControl(CacheControl.noCache()) // atur sesuai kebutuhan
                    .body(res);
        }

        // partial content (ambil range pertama saja)
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(length);
        long end   = range.getRangeEnd(length);
        long chunkLen = end - start + 1;

        InputStreamResource res = new InputStreamResource(new ByteArrayInputStream(bytes, (int) start, (int) chunkLen));

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(chunkLen)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .body(res);
    }

}

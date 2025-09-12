package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.service.UserImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class UserImportApi {

    private final UserImportService service;

    // Import CSV (multipart/form-data)
    @PostMapping(value="/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserImportService.UploadResult importCsv(@RequestPart("file") MultipartFile file) throws Exception {
        return service.importCsv(file.getInputStream());
    }
}

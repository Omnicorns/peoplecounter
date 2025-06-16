package com.sarinah.peoplecounter.controller;


import com.sarinah.peoplecounter.service.GetAllPeopleCountingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/api/people-counts")
public class PeopleCountingController {
    private final GetAllPeopleCountingService getAllPeopleCountingService;

    @Autowired
    public PeopleCountingController(GetAllPeopleCountingService getAllPeopleCountingService, GetAllPeopleCountingService getAllPeopleCountingService1) {
        this.getAllPeopleCountingService = getAllPeopleCountingService1;

    }

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "name", required = false)  String name,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(value = "endDate",   required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)   Date   endDate
    ) {

        return ResponseEntity.ok(
                getAllPeopleCountingService.search(name, startDate, endDate, page, size)
        );


    }

}

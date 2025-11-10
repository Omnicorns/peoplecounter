package com.sarinah.peoplecounter.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.service.PostEmployeeLoginInjourneyService;
import com.sarinah.peoplecounter.service.PostGetEmployeeInjourneyService;
import com.sarinah.peoplecounter.service.PostScanBarcodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sarinah-forwarder/v1/injourney")
public class InjourneyEmployeeController {
    private final PostEmployeeLoginInjourneyService postEmployeeLoginInjourneyService;
    private final PostGetEmployeeInjourneyService postGetEmployeeInjourneyService;

    @PostMapping(value = "/employee/login")
    public ObjectNode postScanResponse(@RequestBody ObjectNode request) {
        return postEmployeeLoginInjourneyService.execute(request);
    }

    @PostMapping(value = "/employee")
    public ObjectNode getEmployee(@RequestBody ObjectNode request) {
        return postGetEmployeeInjourneyService.execute(request);
    }

}

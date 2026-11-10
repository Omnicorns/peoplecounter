package com.sarinah.peoplecounter.service;

import tools.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostScanDevBarcodeService {
    private final SarinahGetModulAdaptor sarinahGetModulServiceAdaptor;


    public ObjectNode execute(ObjectNode request) {
        return sarinahGetModulServiceAdaptor.getScanBarcodeDev(request);

    }

}


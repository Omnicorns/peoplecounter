package com.sarinah.peoplecounter.service;


import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostScanBarcodeService {
    private final SarinahGetModulAdaptor sarinahGetModulServiceAdaptor;


    public ObjectNode execute(ObjectNode request) {
        return sarinahGetModulServiceAdaptor.getScanBarcode(request);

    }

}

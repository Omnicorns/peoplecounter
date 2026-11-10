package com.sarinah.peoplecounter.service;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostPartnerService {
    private final SarinahGetModulAdaptor sarinahGetModulServiceAdaptor;

    public ArrayNode execute(ObjectNode request) {
        return sarinahGetModulServiceAdaptor.getQuerySelect(request);

    }
}

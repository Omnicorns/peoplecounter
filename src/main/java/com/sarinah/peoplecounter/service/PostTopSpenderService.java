package com.sarinah.peoplecounter.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostTopSpenderService {
    private final SarinahGetModulAdaptor sarinahGetModulServiceAdaptor;


    public ArrayNode execute(ObjectNode request) {
        return sarinahGetModulServiceAdaptor.getTopSpender(request);

    }
}

package com.sarinah.peoplecounter.service;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class  PostJournalEntriesService {
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;


    public ArrayNode execute(ObjectNode request) {
        return sarinahGetModulAdaptor.getJournalEntries(request);

    }
}

package com.sarinah.peoplecounter.service;


import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.InjourneyGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostGetEmployeeInjourneyService {
    private final InjourneyGetModulAdaptor injourneyGetModulAdaptor;

    public ObjectNode execute(ObjectNode request) {
        return injourneyGetModulAdaptor.getDataKaryawan(request);

    }
}

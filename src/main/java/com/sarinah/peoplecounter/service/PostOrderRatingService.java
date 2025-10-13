package com.sarinah.peoplecounter.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostOrderRatingService {
    private final SarinahGetModulAdaptor sarinahGetModulServiceAdaptor;


    public ArrayNode execute(ObjectNode request) {
        return sarinahGetModulServiceAdaptor.getPosOrderRating(request);

    }
}

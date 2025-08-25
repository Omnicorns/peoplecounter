package com.sarinah.peoplecounter.adaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Component
public class SarinahGetModulAdaptor {
    @Value("${sarinah-portal.barcode.url}")
    private String barcodeUrl;


    private final CommonUtils commonUtils;
    private final RestClient defaultPointRestClient;

    public ObjectNode getScanBarcode(ObjectNode request) {

        ObjectNode root = defaultPointRestClient
                .post()
                .uri(commonUtils.dynamicParamBuilder(request,barcodeUrl))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObjectNode.class);;                 // baca sebagai JsonNode



        return root;
    }

}

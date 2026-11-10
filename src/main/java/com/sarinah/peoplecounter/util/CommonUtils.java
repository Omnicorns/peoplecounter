package com.sarinah.peoplecounter.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommonUtils {

    public URI dynamicParamBuilder (ObjectNode request, String url) {
        UriComponentsBuilder param = UriComponentsBuilder.fromUriString(url);
        Iterator<Map.Entry<String, JsonNode>> requestIterator = request.properties().iterator();

        while (requestIterator.hasNext()) {
            Map.Entry<String, JsonNode> requestEntry = requestIterator.next();
            param.queryParam(requestEntry.getKey(), requestEntry.getValue().asString());
        }
        return param.build().encode().toUri();
    }


    public MultiValueMap<String, String> dynamicFormDataBuilder(ObjectNode request) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        Iterator<Map.Entry<String, JsonNode>> requestIterator = request.properties().iterator();
        while (requestIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = requestIterator.next();
            formData.add(entry.getKey(), entry.getValue().asString());
        }
        return formData;
    }
}

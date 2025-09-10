package com.sarinah.peoplecounter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class PostLoyaltyMemberService {
    private final SarinahGetModulAdaptor sarinahGetModulServiceAdaptor;


    public ArrayNode execute(ObjectNode request) {
       ArrayNode response = sarinahGetModulServiceAdaptor.getLoyalty(request);
        for (JsonNode node : response) {
            if (node.isObject()) {
                ObjectNode obj = (ObjectNode) node;

                // hapus field yang tidak perlu
                obj.remove(Arrays.asList(
                        "is_loyalty",
                        "is_customer",
                        "is_property",
                        "mobile",
                        "barcode",
                        "loyalty_id"
                ));

                // masking phone
                if (obj.hasNonNull("phone")) {
                    String phone = obj.get("phone").asText();
                    int len = phone.length();
                    if (len > 3) {
                        obj.put("phone", phone.substring(0, len - 3) + "***");
                    } else {
                        obj.put("phone", "***");
                    }
                }
            }
        }
        return response;
    }



}

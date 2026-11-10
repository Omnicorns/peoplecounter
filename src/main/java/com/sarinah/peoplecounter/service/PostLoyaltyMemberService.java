package com.sarinah.peoplecounter.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
                for (String hidden : List.of(
                        "is_loyalty",
                        "is_customer",
                        "is_property",
                        "mobile",
                        "barcode",
                        "loyalty_id"
                )) {
                    obj.remove(hidden);
                }

                // masking phone
                if (obj.hasNonNull("phone")) {
                    String phone = obj.get("phone").asString();
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

package com.sarinah.peoplecounter.adaptor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
@RequiredArgsConstructor
@Component
public class InjourneyGetModulAdaptor {
    @Value("${injourney-portal.employee-login.url}")
    private String employeeLoginUrl;
    @Value("${injourney-portal.employee-data-karyawan.url}")
    private String dataKaryawanUrl;
    private final CommonUtils commonUtils;
    private final RestClient defaultPointRestClient;


    public ObjectNode getEmployeeLogin(ObjectNode request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", request.get("username").asText());
        form.add("password", request.get("password").asText());


        ObjectNode root = defaultPointRestClient
                .post()
                .uri(employeeLoginUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObjectNode.class);;                 // baca sebagai JsonNode

        return root;
    }

    public ObjectNode getDataKaryawan(ObjectNode request) {
        return defaultPointRestClient.get()
                .uri(dataKaryawanUrl)
                .headers(h -> h.setBearerAuth(request.get("token").asText()))
                .retrieve()
                .body(ObjectNode.class);
    }




}

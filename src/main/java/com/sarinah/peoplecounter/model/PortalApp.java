package com.sarinah.peoplecounter.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PortalApp {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String url;
    private String loginUrl;
    private String roles;
    private Map<String, String> formFields = new LinkedHashMap<>();
}

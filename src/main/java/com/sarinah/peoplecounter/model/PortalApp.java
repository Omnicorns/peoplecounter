package com.sarinah.peoplecounter.model;

import lombok.Data;

import java.util.List;

@Data
public class PortalApp {
    private String id;
    private String name;
    private String url;
    private String icon;
    private String description;
    private List<String> roles;
}

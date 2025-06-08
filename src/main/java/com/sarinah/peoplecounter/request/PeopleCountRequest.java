package com.sarinah.peoplecounter.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeopleCountRequest {
    private String day;
    private String date;
    private String name;
    private String in;
    private String out;
    private String avg;
    private Date dateInbound;
    private String filename;


}

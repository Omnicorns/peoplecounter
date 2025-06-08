package com.sarinah.peoplecounter.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeopleCountResponse {
    private String day;
    private String date;
    private String name;
    private String in;
    private String out;
    private String avg;
}

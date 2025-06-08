package com.sarinah.peoplecounter.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;


@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "people_count")
public class PeopleCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "day")
    private String day;

    @Column (name = "count_date")
    private Date countDate;

    @Column (name = "in_count", precision = 10, scale = 3, nullable = false)
    private BigDecimal inCount;

    @Column (name = "out_count", precision = 10, scale = 3, nullable = false)
    private BigDecimal outCount;

    @Column(name = "avg_count", precision = 10, scale = 3, nullable = false)
    private BigDecimal avgCount;

    @Column (name="date_inbound")
    private Timestamp dateInbound;

    @Column (name ="filename")
    private String filename;



}

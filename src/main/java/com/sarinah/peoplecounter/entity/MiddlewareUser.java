package com.sarinah.peoplecounter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Builder
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "middleware_users")
public class MiddlewareUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=50)
    private String username;

    @Column(nullable=false, length=255)
    private String password;  // plaintext

    @Column(nullable=false, length=20)
    private String status = "ACTIVE";  // pakai string biasa

    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt = Instant.now();

}

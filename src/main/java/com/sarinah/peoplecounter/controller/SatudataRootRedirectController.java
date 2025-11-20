package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.service.SatudataRouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping // root, tanpa prefix
public class SatudataRootRedirectController {
    private final SatudataRouteService routeService;

    public SatudataRootRedirectController(SatudataRouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * Root landing, supaya "/" tidak ikut ke-mapping ke pathKey
     */
//    @GetMapping("/")
//    public ResponseEntity<String> home() {
//        String body = """
//                <html>
//                  <head><title>SatuData Router</title></head>
//                  <body>
//                    <h1>SatuData Router</h1>
//                    <p>Gunakan path seperti <code>/hris</code>, <code>/catalogue</code>, dll.</p>
//                  </body>
//                </html>
//                """;
//        return ResponseEntity.ok()
//                .header("Content-Type", "text/html; charset=UTF-8")
//                .body(body);
//    }

    /**
     * MODE 1: langsung redirect
     * Contoh:
     *  - GET /hris         -> redirect ke URL di DB
     *  - GET /catalogue    -> redirect ke URL di DB
     *  - GET /peoplecounter -> redirect ke URL di DB
     */
    @GetMapping("/browse/{pathKey}")
    public ResponseEntity<?> redirectByPathKey(@PathVariable String pathKey) {
        return routeService.findActiveByPathKey(pathKey)
                .map(route -> ResponseEntity
                        .status(HttpStatus.FOUND) // 302
                        .location(URI.create(route.getTargetUrl()))
                        .build()
                )
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * MODE 2: endpoint JSON (opsional)
     * Contoh:
     *  - GET /dns/hris -> balikin JSON berisi targetUrl
     */
//    @GetMapping("/dns/{pathKey}")
//    public ResponseEntity<DnsResponseDto> getTargetInfo(@PathVariable String pathKey) {
//        return routeService.findActiveByPathKey(pathKey)
//                .map(route -> ResponseEntity.ok(
//                        new DnsResponseDto(
//                                true,
//                                route.getPathKey(),
//                                route.getTargetUrl(),
//                                "OK"
//                        )
//                ))
//                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(new DnsResponseDto(
//                                false,
//                                pathKey,
//                                null,
//                                "Path tidak ditemukan atau nonaktif"
//                        )));
//    }
}

package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.entity.SatudataRoute;
import com.sarinah.peoplecounter.service.SatudataRouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

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
//  @GetMapping("/browse/{pathKey}")
//  public ResponseEntity<?> redirectByPathKey(@PathVariable String pathKey) {
//      return routeService.findActiveByPathKey(pathKey)
//                .map(route -> ResponseEntity
//                        .status(HttpStatus.FOUND) // 302
//                        .location(URI.create(route.getTargetUrl()))
//                        .build()
//                )
//                .orElseGet(() -> ResponseEntity.notFound().build());
//    }
    @GetMapping("/browse/{pathKey}")
    public ResponseEntity<?> browse(@PathVariable String pathKey) {

        List<SatudataRoute> routes = routeService.findAllActiveByPathKey(pathKey);

        if (routes.isEmpty()) {
            return ResponseEntity.notFound().build();
        }


        if (routes.size() == 1) {
          SatudataRoute route = routes.get(0);
           return ResponseEntity
                   .status(HttpStatus.FOUND)
                    .location(URI.create(route.getTargetUrl()))
                    .build();
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang=\"id\">");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\"/>");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        html.append("<title>Sarinah - Pilih Tujuan</title>");
        html.append("<style>");
        html.append("*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}");
        html.append("body{font-family:system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;");
        html.append("background:radial-gradient(circle at top,#e0f2fe 0,#f9fafb 45%,#eef2ff 100%);");
        html.append("color:#111827;padding:24px;display:flex;justify-content:center;}");

        html.append(".page{width:100%;max-width:720px;margin:auto;");
        html.append("background:#ffffff;border-radius:20px;padding:20px 20px 18px;");
        html.append("box-shadow:0 20px 45px rgba(15,23,42,.12);");
        html.append("border:1px solid rgba(148,163,184,.25);}");

        html.append(".top-bar{display:flex;align-items:center;justify-content:space-between;");
        html.append("margin-bottom:16px;font-size:12px;color:#6b7280;}");
        html.append(".brand{display:flex;align-items:center;gap:8px;font-weight:600;color:#111827;}");
        html.append(".brand-icon{width:26px;height:26px;border-radius:999px;");
        html.append("background:conic-gradient(from 210deg,#22c55e,#16a34a,#22c55e);");
        html.append("display:flex;align-items:center;justify-content:center;");
        html.append("color:#ecfdf5;font-size:14px;font-weight:700;");
        html.append("box-shadow:0 8px 20px rgba(34,197,94,.35);}");
        html.append(".brand-sub{font-size:11px;color:#9ca3af;}");

        html.append(".header{margin-bottom:18px;text-align:left;}");
        html.append(".badge{display:inline-flex;align-items:center;font-size:11px;");
        html.append("text-transform:uppercase;letter-spacing:.08em;padding:4px 10px;");
        html.append("border-radius:999px;border:1px solid rgba(148,163,184,.4);color:#6b7280;background:#f9fafb;}");
        html.append(".dot{width:6px;height:6px;border-radius:999px;background:#22c55e;margin-right:6px;}");
        html.append("h1{font-size:26px;line-height:1.2;margin:12px 0 6px;color:#020617;}");
        html.append(".sub{font-size:14px;color:#6b7280;}");

        html.append(".list{display:flex;flex-direction:column;gap:12px;margin-top:20px;}");

        html.append(".card{position:relative;border-radius:14px;border:1px solid #e5e7eb;");
        html.append("background:#ffffff;overflow:hidden;");
        html.append("padding:14px 16px;transition:transform .16s ease,box-shadow .16s ease,border-color .16s ease;");
        html.append("box-shadow:0 10px 30px rgba(15,23,42,.06);}");
        html.append(".card::before{content:\"\";position:absolute;inset:0;pointer-events:none;");
        html.append("background:radial-gradient(circle at top right,rgba(34,197,94,.14),transparent 55%);");
        html.append("opacity:0;transition:opacity .2s ease;}");
        html.append(".card:hover{transform:translateY(-2px);border-color:#22c55e;");
        html.append("box-shadow:0 18px 40px rgba(34,197,94,.16);}");
        html.append(".card:hover::before{opacity:1;}");

        html.append(".card-header{position:relative;display:flex;justify-content:space-between;");
        html.append("align-items:flex-start;gap:8px;z-index:1;}");
        html.append(".card-title{font-size:15px;font-weight:600;color:#111827;word-break:break-word;}");
        html.append(".card-tag{font-size:11px;padding:2px 8px;border-radius:999px;");
        html.append("background:rgba(220,252,231,.9);color:#166534;");
        html.append("border:1px solid rgba(22,163,74,.25);white-space:nowrap;}");

        html.append(".card-desc{position:relative;font-size:13px;color:#6b7280;");
        html.append("margin:8px 0 12px;min-height:16px;z-index:1;}");

        html.append(".card-footer{position:relative;display:flex;justify-content:space-between;");
        html.append("align-items:center;gap:8px;font-size:11px;color:#9ca3af;z-index:1;}");

        html.append(".btn-primary{display:inline-flex;align-items:center;justify-content:center;");
        html.append("padding:6px 12px;border-radius:999px;border:none;font-size:12px;font-weight:500;");
        html.append("background:linear-gradient(135deg,#22c55e,#16a34a);color:#f9fafb;");
        html.append("text-decoration:none;cursor:pointer;white-space:nowrap;");
        html.append("box-shadow:0 8px 20px rgba(34,197,94,.35);");
        html.append("transition:transform .14s ease,box-shadow .14s ease,filter .14s ease;}");
        html.append(".btn-primary span{margin-left:6px;font-size:13px;}");
        html.append(".btn-primary:hover{transform:translateY(-1px);");
        html.append("box-shadow:0 14px 30px rgba(34,197,94,.35);filter:brightness(1.03);}");

        html.append(".path-pill{padding:3px 8px;border-radius:999px;background:#f9fafb;");
        html.append("border:1px solid #e5e7eb;color:#4b5563;");
        html.append("font-family:ui-monospace,Menlo,monospace;font-size:11px;}");

        html.append("footer{margin-top:22px;font-size:11px;color:#9ca3af;text-align:left;}");
        html.append("footer span{font-weight:600;color:#111827;}");

        html.append("@media(max-width:480px){");
        html.append("body{padding:16px;}");
        html.append(".page{padding:16px 14px 14px;border-radius:16px;}");
        html.append("h1{font-size:22px;}");
        html.append(".card{padding:12px 12px;}");
        html.append(".top-bar{flex-direction:column;align-items:flex-start;gap:4px;}");
        html.append("}");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class=\"page\">");

        html.append("<div class=\"top-bar\">");
        html.append("<div class=\"brand\">");
        html.append("<div class=\"brand-icon\">S</div>");
        html.append("<div>");
        html.append("<div>Sarinah Short Url</div>");
        html.append("<div class=\"brand-sub\">Satu tautan, beberapa tujuan informasi</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("<div class=\"brand-sub\">satudata.sarinah.com</div>");
        html.append("</div>");

        html.append("<div class=\"header\">");
        html.append("<div class=\"badge\"><span class=\"dot\"></span>Pilih tujuan informasi</div>");
        html.append("<h1>Pilih informasi yang ingin dibuka</h1>");
        html.append("<p class=\"sub\">Tautan ini memiliki beberapa tujuan. Silakan pilih sesuai kebutuhan Anda.</p>");
        html.append("</div>");

        html.append("<div class=\"list\">");

        for (SatudataRoute r : routes) {
            String desc = r.getDescription() != null ? r.getDescription() : "";
            String s = r.getPathKey() != null ? r.getPathKey() : "";
            String url = r.getTargetUrl() != null ? r.getTargetUrl() : "#";

            html.append("<div class=\"card\">");

            html.append("<div class=\"card-header\">");
            html.append("<div class=\"card-title\">")
                    .append(desc.isBlank() ? "Detail informasi" : desc)
                    .append("</div>");
            html.append("<div class=\"card-tag\">Tautan tujuan</div>");
            html.append("</div>");

            html.append("<div class=\"card-desc\">")
                    .append(desc.isBlank() ? "Klik tombol di bawah untuk melihat detail informasi." : desc)
                    .append("</div>");

            html.append("<div class=\"card-footer\">");
            html.append("<div class=\"path-pill\">")
                    .append(s)
                    .append("</div>");
            html.append("<a class=\"btn-primary\" href=\"")
                    .append(url)
                    .append("\">Buka detail<span>↗</span></a>");
            html.append("</div>");

            html.append("</div>"); // .card
        }

        html.append("</div>"); // .list
        html.append("<footer>Powered by <span>Sarinah</span></footer>");
        html.append("</div>"); // .page
        html.append("</body>");
        html.append("</html>");




        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html.toString());
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

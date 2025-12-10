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
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;");
        html.append("background:linear-gradient(135deg,#f0fdf4 0%,#fefce8 50%,#f0f9ff 100%);");
        html.append("color:#020617;padding:24px;display:flex;justify-content:center;align-items:center;min-height:100vh;");
        html.append("position:relative;}");

        html.append("body::before{content:\"\";position:fixed;inset:0;");
        html.append("background:radial-gradient(circle at 20% 30%, rgba(34,197,94,0.08) 0%, transparent 50%),");
        html.append("radial-gradient(circle at 80% 70%, rgba(59,130,246,0.06) 0%, transparent 50%);");
        html.append("pointer-events:none;z-index:0;}");

        html.append(".page{width:100%;max-width:720px;margin:auto;position:relative;overflow:hidden;");
        html.append("background:#ffffff;border-radius:24px;padding:32px;");
        html.append("box-shadow:0 24px 60px rgba(15,23,42,.12),0 0 0 1px rgba(148,163,184,.15);");
        html.append("z-index:1;}");

        html.append(".page::before{content:\"\";position:absolute;top:-50%;left:-50%;width:200%;height:200%;");
        html.append("background:radial-gradient(circle at center,rgba(34,197,94,0.03) 0%,transparent 70%);");
        html.append("pointer-events:none;opacity:0.6;}");
        html.append(".page-content{position:relative;z-index:1;}");

        html.append(".top-bar{display:flex;align-items:center;justify-content:space-between;");
        html.append("margin-bottom:28px;padding-bottom:20px;");
        html.append("border-bottom:2px solid rgba(34,197,94,.1);}");

        html.append(".brand{display:flex;align-items:center;gap:12px;}");
        html.append(".brand-icon{width:48px;height:48px;border-radius:14px;");
        html.append("background:linear-gradient(135deg,#22c55e,#16a34a);");
        html.append("display:flex;align-items:center;justify-content:center;");
        html.append("color:#ffffff;font-size:22px;font-weight:700;");
        html.append("box-shadow:0 12px 28px rgba(34,197,94,.35),inset 0 1px 0 rgba(255,255,255,0.3);");
        html.append("position:relative;}");

        html.append(".brand-icon::after{content:\"\";position:absolute;inset:0;border-radius:14px;");
        html.append("background:linear-gradient(180deg,rgba(255,255,255,0.25),transparent);");
        html.append("pointer-events:none;}");

        html.append(".brand-main{display:flex;flex-direction:column;gap:3px;}");
        html.append(".brand-title{font-size:17px;font-weight:700;color:#111827;}");
        html.append(".brand-sub{font-size:12px;color:#6b7280;}");

        html.append(".status-badge{display:inline-flex;align-items:center;gap:6px;");
        html.append("padding:6px 14px;border-radius:999px;");
        html.append("background:linear-gradient(135deg,rgba(34,197,94,0.12),rgba(34,197,94,0.08));");
        html.append("border:1.5px solid rgba(34,197,94,.3);");
        html.append("font-size:11px;font-weight:600;color:#16a34a;");
        html.append("text-transform:uppercase;letter-spacing:.05em;}");

        html.append(".status-dot{width:7px;height:7px;border-radius:999px;");
        html.append("background:#22c55e;box-shadow:0 0 8px rgba(34,197,94,.6);");
        html.append("animation:pulse 2s ease-in-out infinite;}");

        html.append("@keyframes pulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:0.6;transform:scale(0.9)}}");

        html.append(".header{margin-bottom:24px;}");
        html.append(".header-badge{display:inline-flex;align-items:center;gap:6px;");
        html.append("padding:6px 14px;border-radius:999px;");
        html.append("background:linear-gradient(135deg,rgba(59,130,246,0.1),rgba(59,130,246,0.05));");
        html.append("border:1.5px solid rgba(59,130,246,.25);");
        html.append("font-size:12px;font-weight:600;color:#2563eb;margin-bottom:14px;}");

        html.append("h1{font-size:32px;font-weight:800;line-height:1.2;margin-bottom:10px;color:#020617;");
        html.append("background:linear-gradient(135deg,#020617 0%,#334155 100%);");
        html.append("-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;}");

        html.append(".sub{font-size:15px;color:#64748b;line-height:1.6;}");

        html.append(".list{display:flex;flex-direction:column;gap:14px;margin-top:24px;}");

        html.append(".card{position:relative;border-radius:20px;");
        html.append("background:#ffffff;overflow:hidden;");
        html.append("padding:22px;transition:all 0.3s cubic-bezier(0.4,0,0.2,1);");
        html.append("border:2px solid rgba(226,232,240,.8);");
        html.append("box-shadow:0 4px 16px rgba(15,23,42,.06);cursor:pointer;}");

        html.append(".card::before{content:\"\";position:absolute;inset:0;pointer-events:none;");
        html.append("background:linear-gradient(135deg,rgba(34,197,94,.06),transparent 60%);");
        html.append("opacity:0;transition:opacity 0.3s ease;}");

        html.append(".card::after{content:\"\";position:absolute;inset:-2px;border-radius:20px;");
        html.append("padding:2px;background:linear-gradient(135deg,#22c55e,#3b82f6);");
        html.append("-webkit-mask:linear-gradient(#fff 0 0) content-box,linear-gradient(#fff 0 0);");
        html.append("mask:linear-gradient(#fff 0 0) content-box,linear-gradient(#fff 0 0);");
        html.append("-webkit-mask-composite:xor;mask-composite:exclude;opacity:0;transition:opacity 0.3s ease;}");

        html.append(".card:hover{transform:translateY(-4px);");
        html.append("box-shadow:0 20px 40px rgba(34,197,94,.15),0 0 0 2px rgba(34,197,94,.2);}");
        html.append(".card:hover::before{opacity:1;}");
        html.append(".card:hover::after{opacity:1;}");

        html.append(".card-header{position:relative;display:flex;justify-content:space-between;");
        html.append("align-items:flex-start;gap:12px;z-index:1;margin-bottom:12px;}");

        html.append(".card-title-wrap{flex:1;min-width:0;}");
        html.append(".card-title{font-size:18px;font-weight:700;color:#111827;");
        html.append("display:flex;align-items:center;gap:10px;margin-bottom:8px;}");

        html.append(".card-icon{width:36px;height:36px;border-radius:10px;");
        html.append("background:linear-gradient(135deg,rgba(34,197,94,0.15),rgba(34,197,94,0.08));");
        html.append("border:1.5px solid rgba(34,197,94,.25);");
        html.append("display:flex;align-items:center;justify-content:center;");
        html.append("font-size:18px;flex-shrink:0;}");

        html.append(".card-path{font-size:12px;color:#64748b;");
        html.append("font-family:ui-monospace,'SF Mono',Monaco,Menlo,monospace;");
        html.append("background:rgba(241,245,249,.8);padding:5px 12px;border-radius:8px;");
        html.append("display:inline-block;border:1px solid rgba(226,232,240,.8);}");

        html.append(".card-tag{font-size:11px;padding:5px 12px;border-radius:999px;");
        html.append("background:linear-gradient(135deg,rgba(34,197,94,0.15),rgba(34,197,94,0.1));");
        html.append("color:#16a34a;border:1.5px solid rgba(34,197,94,.3);");
        html.append("white-space:nowrap;font-weight:600;flex-shrink:0;}");

        html.append(".card-desc{position:relative;font-size:14px;color:#64748b;");
        html.append("margin-bottom:18px;line-height:1.7;z-index:1;}");

        html.append(".card-footer{position:relative;display:flex;justify-content:space-between;");
        html.append("align-items:center;gap:12px;padding-top:18px;");
        html.append("border-top:1.5px solid rgba(226,232,240,.6);z-index:1;}");

        html.append(".meta{display:flex;align-items:center;gap:8px;font-size:12px;color:#64748b;}");
        html.append(".meta-icon{font-size:14px;opacity:0.7;}");

        html.append(".btn-primary{display:inline-flex;align-items:center;justify-content:center;");
        html.append("gap:6px;padding:11px 20px;border-radius:12px;border:none;font-size:14px;font-weight:600;");
        html.append("background:linear-gradient(135deg,#22c55e,#16a34a);color:#ffffff;");
        html.append("text-decoration:none;cursor:pointer;white-space:nowrap;");
        html.append("box-shadow:0 10px 24px rgba(34,197,94,.3),inset 0 1px 0 rgba(255,255,255,0.25);");
        html.append("transition:all 0.2s ease;position:relative;overflow:hidden;}");

        html.append(".btn-primary::before{content:\"\";position:absolute;inset:0;");
        html.append("background:linear-gradient(135deg,rgba(255,255,255,0.2),transparent);");
        html.append("opacity:0;transition:opacity 0.2s ease;}");

        html.append(".btn-primary:hover{transform:translateY(-2px);");
        html.append("box-shadow:0 14px 32px rgba(34,197,94,.4),inset 0 1px 0 rgba(255,255,255,0.35);}");
        html.append(".btn-primary:hover::before{opacity:1;}");

        html.append(".btn-arrow{font-size:16px;transition:transform 0.2s ease;}");
        html.append(".btn-primary:hover .btn-arrow{transform:translateX(3px);}");

        html.append("footer{margin-top:32px;padding-top:20px;");
        html.append("border-top:1.5px solid rgba(226,232,240,.6);");
        html.append("font-size:12px;color:#64748b;text-align:center;}");
        html.append("footer span{font-weight:700;color:#111827;}");

        html.append("@media(max-width:640px){");
        html.append("body{padding:16px;}");
        html.append(".page{padding:24px 20px;border-radius:20px;}");
        html.append("h1{font-size:26px;}");
        html.append(".card{padding:18px;}");
        html.append(".top-bar{flex-direction:column;align-items:flex-start;gap:12px;}");
        html.append(".status-badge{align-self:flex-start;}");
        html.append(".card-header{flex-direction:column;align-items:flex-start;}");
        html.append(".card-tag{align-self:flex-start;}");
        html.append(".card-footer{flex-direction:column;align-items:stretch;}");
        html.append(".btn-primary{width:100%;justify-content:center;}");
        html.append(".meta{justify-content:center;}");
        html.append("}");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class=\"page\">");
        html.append("<div class=\"page-content\">");

        html.append("<div class=\"top-bar\">");
        html.append("<div class=\"brand\">");
        html.append("<div class=\"brand-icon\">S</div>");
        html.append("<div class=\"brand-main\">");
        html.append("<div class=\"brand-title\">Sarinah Short URL</div>");
        html.append("<div class=\"brand-sub\">Satu tautan, beberapa tujuan</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("<div class=\"status-badge\">");
        html.append("<span class=\"status-dot\"></span>");
        html.append("Aktif");
        html.append("</div>");
        html.append("</div>");

        html.append("<div class=\"header\">");
        html.append("<div class=\"header-badge\">📍 Pilih Tujuan</div>");
        html.append("<h1>Pilih informasi yang ingin dibuka</h1>");
        html.append("<p class=\"sub\">Tautan ini memiliki beberapa tujuan. Silakan pilih sesuai kebutuhan Anda.</p>");
        html.append("</div>");

        html.append("<div class=\"list\">");

        for (SatudataRoute r : routes) {
            String desc = r.getDescription() != null ? r.getDescription() : "";
            String s = r.getPathKey() != null ? r.getPathKey() : "";
            String url = r.getTargetUrl() != null ? r.getTargetUrl() : "#";

            html.append("<div class=\"card\" onclick=\"location.href='").append(url).append("'\">");

            html.append("<div class=\"card-header\">");
            html.append("<div class=\"card-title-wrap\">");
            html.append("<div class=\"card-title\">");
            html.append("<span class=\"card-icon\">📄</span>");
            html.append(desc.isBlank() ? "Detail informasi" : desc);
            html.append("</div>");
            html.append("<div class=\"card-path\">/").append(s).append("</div>");
            html.append("</div>");
            html.append("<div class=\"card-tag\">Tautan</div>");
            html.append("</div>");

            html.append("<div class=\"card-desc\">");
            html.append(desc.isBlank() ? "Klik tombol di bawah untuk melihat detail informasi." : desc);
            html.append("</div>");

            html.append("<div class=\"card-footer\">");
            html.append("<div class=\"meta\">");
            html.append("<span class=\"meta-icon\">🔗</span>");
            html.append("<span>").append(s).append("</span>");
            html.append("</div>");
            html.append("<a class=\"btn-primary\" href=\"").append(url).append("\" onclick=\"event.stopPropagation()\">");
            html.append("Buka Sekarang");
            html.append("<span class=\"btn-arrow\">→</span>");
            html.append("</a>");
            html.append("</div>");

            html.append("</div>"); // .card
        }

        html.append("</div>"); // .list
        html.append("<footer>Powered by <span>Sarinah</span> • Short URL Manager</footer>");
        html.append("</div>"); // .page-content
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

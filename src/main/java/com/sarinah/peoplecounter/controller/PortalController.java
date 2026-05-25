package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.configuration.PortalProperties;
import com.sarinah.peoplecounter.model.PortalApp;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PortalController {

    @GetMapping("/townhall")
   public String loginPage(Model model) {
        return "portal2";
    }

    @GetMapping("/booth/{kode}")
    public String booth(@PathVariable String kode) {
        if (kode.equalsIgnoreCase("hc")) {
            return "booth-hc";
        }

        if (kode.equalsIgnoreCase("it")) {
            return "booth-it";
        }

        if (kode.equalsIgnoreCase("marketing")) {
            return "booth-marketing";
        }

        return "redirect:/townhall";
    }

    @GetMapping("/quiz-booth3")
    public String quizBooth3(Model model) {

        model.addAttribute("pageTitle", "Interactive Quiz Booth 3");

        return "quiz-booth3";

    }






//    @Autowired
//    private PortalProperties portalProperties;
//
//    @GetMapping("/portal/login")
//    public String loginPage(
//            @RequestParam(value = "error", required = false) String error,
//            @RequestParam(value = "logout", required = false) String logout,
//            @RequestParam(value = "expired", required = false) String expired,
//            Model model) {
//
//        if (error != null) {
//            model.addAttribute("errorMessage", "Username atau password salah. Pastikan menggunakan akun Active Directory.");
//        }
//        if (logout != null) {
//            model.addAttribute("logoutMessage", "Anda berhasil logout.");
//        }
//        if (expired != null) {
//            model.addAttribute("errorMessage", "Sesi Anda telah berakhir. Silakan login kembali.");
//        }
//
//        return "login";
//    }
//
//    @GetMapping("/portal")
//    public String dashboard(Model model) {
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        String username = auth.getName();
//
//        model.addAttribute("username", extractDisplayName(username));
//        model.addAttribute("apps", portalProperties.getApps());
//        return "portal";
//    }
//
//    @GetMapping("/portal/launch/{id}")
//    public String launch(@PathVariable String id, HttpSession session, Model model) {
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        String username = auth.getName();
//        String password = (String) session.getAttribute("relayPassword");
//
//        PortalApp app = portalProperties.getApps().stream()
//                .filter(a -> a.getId().equals(id))
//                .findFirst().orElseThrow();
//
//        Map<String, String> resolvedFields = new LinkedHashMap<>();
//        app.getFormFields().forEach((field, valueKey) -> {
//            if ("username".equals(valueKey))       resolvedFields.put(field, username);
//            else if ("password".equals(valueKey))  resolvedFields.put(field, password);
//            else if ("FETCH_CSRF".equals(valueKey)) {
//                // Fetch CSRF dari server target
//                String csrf = fetchCsrfToken(app.getLoginUrl());
//                if (!csrf.isEmpty()) {
//                    resolvedFields.put(field, csrf);
//                }
//            }
//            else resolvedFields.put(field, valueKey);
//        });
//
//        model.addAttribute("targetUrl", app.getLoginUrl());
//        model.addAttribute("fields", resolvedFields);
//        model.addAttribute("appName", app.getName());
//        return "auto-login";
//    }
//
//    private String fetchCsrfToken(String loginUrl) {
//        try {
//            TrustManager[] trustAll = new TrustManager[]{
//                    new X509TrustManager() {
//                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
//                        public void checkClientTrusted(X509Certificate[] c, String a) {}
//                        public void checkServerTrusted(X509Certificate[] c, String a) {}
//                    }
//            };
//
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(null, trustAll, new java.security.SecureRandom());
//
//            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(
//                    sslContext, NoopHostnameVerifier.INSTANCE);
//
//            // Fix: pasang csf ke connection manager
//            CloseableHttpClient httpClient = HttpClients.custom()
//                    .setConnectionManager(
//                            PoolingHttpClientConnectionManagerBuilder.create()
//                                    .setSSLSocketFactory(csf)
//                                    .build()
//                    )
//                    .build();
//
//            HttpComponentsClientHttpRequestFactory factory =
//                    new HttpComponentsClientHttpRequestFactory(httpClient);
//
//            RestTemplate restTemplate = new RestTemplate(factory);
//            String html = restTemplate.getForObject(loginUrl, String.class);
//
//            Pattern pattern = Pattern.compile(
//                    "name=\"csrf_token\"\\s+content=\"([a-f0-9]+)\"");
//            Matcher matcher = pattern.matcher(html);
//            if (matcher.find()) {
//                log.info("CSRF token fetched OK");
//                return matcher.group(1);
//            }
//        } catch (Exception e) {
//            log.error("Failed to fetch CSRF token from {}", loginUrl, e);
//        }
//        return "";
//    }
//
//    private String extractDisplayName(String username) {
//        if (username.contains("\\")) {
//            username = username.substring(username.indexOf("\\") + 1);
//        }
//        if (username.contains("@")) {
//            username = username.substring(0, username.indexOf("@"));
//        }
//        String[] words = username.replace(".", " ").split(" ");
//        StringBuilder sb = new StringBuilder();
//        for (String word : words) {
//            if (!word.isEmpty()) {
//                sb.append(Character.toUpperCase(word.charAt(0)))
//                        .append(word.substring(1).toLowerCase())
//                        .append(" ");
//            }
//        }
//        return sb.toString().trim();
//    }


}
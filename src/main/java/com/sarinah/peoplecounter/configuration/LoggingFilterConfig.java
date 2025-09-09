package com.sarinah.peoplecounter.configuration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

@Configuration
public class LoggingFilterConfig {

    /** Kunci attribute global (ServletContext) – dibaca Controller/Thymeleaf melalui model */
    public static final String ATTR_LAST_SCAN_USER  = "LAST_SCAN_USER_GLOBAL";
    public static final String ATTR_LAST_SCAN_VALUE = "LAST_SCAN_VALUE_GLOBAL";
    public static final String ATTR_LAST_SCAN_NAME  = "LAST_SCAN_NAME_GLOBAL";   // <<— BARU
    public static final String ATTR_LAST_SCAN_AT    = "LAST_SCAN_AT_GLOBAL";
    public static final String ATTR_SCAN_TODAY      = "SCAN_TODAY_LIST";

    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> loggingFilterRegistration() {
        FilterRegistrationBean<ApiLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiLoggingFilter());
        reg.setName("apiLoggingFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // log duluan
        reg.addUrlPatterns("/*");
        reg.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        return reg;
    }

    static class ApiLoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
        private static final ObjectMapper OM = new ObjectMapper();
        private static final int MAX = 4096;

        /** Ambil {"value":"..."} dari request body */
        private static final Pattern P_VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
        /** Ambil {"name":"..."} dari response body (fallback bila bukan JSON valid) */
        private static final Pattern P_NAME  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

        /** Endpoint yang kita catat riwayatnya */
        private static final String BARCODE_ENDPOINT_PREFIX = "/sarinah-forwarder/v1/modul/barcode";

        @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
        @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            ContentCachingRequestWrapper req  = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            String traceId = headerOrNew(req, "X-Request-ID");
            MDC.put("traceId", traceId);

            // Username dari SESSION (kalau ada). Jika Android beda session, akan "anonymous".
            HttpSession s = request.getSession(false);
            String username = (s != null && s.getAttribute("AUTH_USERNAME") != null)
                    ? String.valueOf(s.getAttribute("AUTH_USERNAME"))
                    : "anonymous";

            if ("anonymous".equals(username)) {
                String ip = request.getRemoteAddr();
                ServletContext ctx = request.getServletContext();

                @SuppressWarnings("unchecked")
                Map<String, String> ipUserMap = (Map<String, String>) ctx.getAttribute("IP_USER_MAP");
                if (ipUserMap != null && ipUserMap.containsKey(ip)) {
                    username = ipUserMap.get(ip);
                }
            }
            MDC.put("username", username);

            long start = System.currentTimeMillis();
            try {
                chain.doFilter(req, res);
            } finally {
                long dur    = System.currentTimeMillis() - start;
                String method = req.getMethod();
                String uri    = req.getRequestURI();
                int status    = res.getStatus();

                String reqBody = sanitize(bytesToString(req.getContentAsByteArray()));
                String resBody = sanitize(bytesToString(res.getContentAsByteArray()));

                // --- Ekstrak nilai scan dari REQUEST body
                String scanVal = extractScanValue(reqBody);

                // --- Ekstrak product name dari RESPONSE body (kalau ada)
                String productName = extractJsonField(resBody, "name");

                if (scanVal != null && !scanVal.isBlank()) {
                    log.info("SCAN user={} value={} name={}", username, scanVal,
                            (productName == null || productName.isBlank()) ? "-" : productName);

                    // (Opsional) simpan ke session – berguna kalau dashboard dan pemindai 1 session
                    if (s != null) {
                        s.setAttribute("LAST_SCAN_USER",  username);
                        s.setAttribute("LAST_SCAN_VALUE", scanVal);
                        s.setAttribute("LAST_SCAN_NAME",  productName);
                        s.setAttribute("LAST_SCAN_AT",    java.time.Instant.now());
                    }

                    // **Utama**: simpan GLOBAL agar terbaca semua session (dashboard browser & Android)
                    ServletContext ctx = request.getServletContext();
                    ctx.setAttribute(ATTR_LAST_SCAN_USER,  username);
                    ctx.setAttribute(ATTR_LAST_SCAN_VALUE, scanVal);
                    ctx.setAttribute(ATTR_LAST_SCAN_NAME,  productName);
                    ctx.setAttribute(ATTR_LAST_SCAN_AT,    java.time.Instant.now());

                    // Catat ke riwayat HARI INI khusus endpoint barcode
                    if (uri != null && uri.startsWith(BARCODE_ENDPOINT_PREFIX)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> buf =
                                (List<Map<String, Object>>) ctx.getAttribute(ATTR_SCAN_TODAY);
                        if (buf == null) {
                            buf = new CopyOnWriteArrayList<>();
                            ctx.setAttribute(ATTR_SCAN_TODAY, buf);
                        }

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("time", LocalDateTime.now()); // LocalDateTime — langsung bisa diformat Thymeleaf
                        row.put("user", username);
                        row.put("value", scanVal);
                        row.put("name",  productName);        // <<— BARU: simpan name dari response
                        row.put("traceId", traceId);

                        buf.add(0, row);                      // prepend biar terbaru di atas
                        while (buf.size() > 1000) {           // trimming sederhana
                            buf.remove(buf.size() - 1);
                        }
                    }
                }

                // Ringkasan + body
                log.info("API {} {} \u2192 status={} ({} ms) traceId={} user={}",
                        method, uri, status, dur, traceId, username);
                if (!reqBody.isBlank()) log.info("reqBody: {}", reqBody);
                if (!resBody.isBlank()) log.info("resBody: {}", resBody);

                res.copyBodyToResponse();
                MDC.remove("traceId");
                MDC.remove("username");
            }
        }

        // ==== Helpers =====================================================================

        private static String headerOrNew(HttpServletRequest req, String name) {
            String v = req.getHeader(name);
            return (v == null || v.isBlank()) ? UUID.randomUUID().toString() : v;
        }

        private static String bytesToString(byte[] arr) {
            return (arr == null || arr.length == 0) ? "" : new String(arr, StandardCharsets.UTF_8);
        }

        private static String sanitize(String raw) {
            if (raw == null || raw.isBlank()) return "";
            String s = raw;
            try { s = OM.writeValueAsString(OM.readTree(raw)); } catch (Exception ignored) {}
            s = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ").trim();
            if (s.length() > MAX) s = s.substring(0, MAX) + "...(truncated)";
            return s;
        }

        /** Ambil field "value" dari request body (JSON atau string mentah) */
        private static String extractScanValue(String body) {
            if (body == null || body.isBlank()) return null;
            // Coba JSON dulu
            try {
                JsonNode n = OM.readTree(body);
                JsonNode v = n.get("value");
                if (v != null && !v.isNull()) return v.asText();
            } catch (Exception ignored) { /* fallback regex */ }
            var m = P_VALUE.matcher(body);
            return m.find() ? m.group(1) : null;
        }

        /** Ambil field generik dari JSON response (di sini kita pakai untuk "name") */
        private static String extractJsonField(String body, String field) {
            if (body == null || body.isBlank()) return null;
            // JSON proper
            try {
                JsonNode n = OM.readTree(body);
                JsonNode v = n.get(field);
                if (v != null && !v.isNull()) return v.asText();
            } catch (Exception ignored) { /* not a JSON, fallback */ }
            // Fallback regex sederhana
            Pattern p = "name".equals(field) ? P_NAME :
                    Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
            var m = p.matcher(body);
            return m.find() ? m.group(1) : null;
        }
    }
}


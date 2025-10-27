package com.sarinah.peoplecounter.configuration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarinah.peoplecounter.entity.ProductScanLog;
import com.sarinah.peoplecounter.entity.ScanSource;
import com.sarinah.peoplecounter.repository.ProductScanLogRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

@Configuration
@RequiredArgsConstructor
public class LoggingFilterConfig {

    /**
     * Kunci attribute global (ServletContext) – dibaca Controller/Thymeleaf melalui model
     */
    public static final String ATTR_LAST_SCAN_USER = "LAST_SCAN_USER_GLOBAL";
    public static final String ATTR_LAST_SCAN_VALUE = "LAST_SCAN_VALUE_GLOBAL";
    public static final String ATTR_LAST_SCAN_NAME = "LAST_SCAN_NAME_GLOBAL";   // <<— BARU
    public static final String ATTR_LAST_SCAN_AT = "LAST_SCAN_AT_GLOBAL";
    public static final String ATTR_SCAN_TODAY = "SCAN_TODAY_LIST";
    public static final String HDR_CID  = "X-Correlation-ID";
    public static final String ATTR_CID = "REQ_CID";
    private  final ProductScanLogRepository productScanLogRepository;

    @Bean
    public ApiLoggingFilter apiLoggingFilter() {
        return new ApiLoggingFilter(productScanLogRepository);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter correlationIdFilter() {
        return new OncePerRequestFilter() {
            @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
            @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                String cid = Optional.ofNullable(request.getHeader(HDR_CID))
                        .filter(s -> !s.isBlank())
                        .orElse(UUID.randomUUID().toString());

                MDC.put("cid", cid);
                request.setAttribute(ATTR_CID, cid);
                response.setHeader(HDR_CID, cid);

                try {
                    filterChain.doFilter(request, response);

                    if (request.isAsyncStarted()) {
                        try {
                            request.getAsyncContext().addListener(new AsyncListener() {
                                @Override public void onComplete(AsyncEvent event) { MDC.remove("cid"); }
                                @Override public void onTimeout(AsyncEvent event) {}
                                @Override public void onError(AsyncEvent event) {}
                                @Override public void onStartAsync(AsyncEvent event) {}
                            });
                        } catch (IllegalStateException ignore) {}
                    }
                } finally {
                    if (!request.isAsyncStarted()) MDC.remove("cid");
                }
            }
        };
    }


    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> loggingFilterRegistration(ApiLoggingFilter filter) {
        FilterRegistrationBean<ApiLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
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

        private final ProductScanLogRepository productScanLogRepository; // ← DI via ctor

        ApiLoggingFilter(ProductScanLogRepository repo) {
            this.productScanLogRepository = repo;
        }


        /**
         * Ambil {"value":"..."} dari request body
         */
        private static final Pattern P_VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
        /**
         * Ambil {"name":"..."} dari response body (fallback bila bukan JSON valid)
         */
        private static final Pattern P_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

        /**
         * Endpoint yang kita catat riwayatnya
         */
        private static final String BARCODE_ENDPOINT_PREFIX = "/sarinah-forwarder/v1/modul/barcode";

        @Override
        protected boolean shouldNotFilterErrorDispatch() {
            return false;
        }

        @Override
        protected boolean shouldNotFilterAsyncDispatch() {
            return false;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            final String cid = getCid(req);
            MDC.put("traceId", cid); // kompat nama lama

            long start = System.currentTimeMillis();
            try {
                // JANGAN resolve username di sini (SecurityContext belum siap)
                chain.doFilter(req, res);

                // === Setelah filter chain lewat, SecurityContext sudah terisi ===
                String username = resolveUsername(req);
                MDC.put("username", username); // set MDC setelahnya

                long dur = System.currentTimeMillis() - start;
                String method = req.getMethod();
                String uri = req.getRequestURI();
                int status = res.getStatus();

                String reqBody = sanitize(bytesToString(req.getContentAsByteArray()));
                String resBody = sanitize(bytesToString(res.getContentAsByteArray()));

                String scanVal = extractScanValue(reqBody);
                String productName = extractJsonField(resBody, "name");

                if (scanVal != null && !scanVal.isBlank()) {
                    log.info("SCAN user={} value={} name={}", username, scanVal,
                            (productName == null || productName.isBlank()) ? "-" : productName);

                    HttpSession s = req.getSession(false);
                    if (s != null) {
                        s.setAttribute("LAST_SCAN_USER", username);
                        s.setAttribute("LAST_SCAN_VALUE", scanVal);
                        s.setAttribute("LAST_SCAN_NAME", productName);
                        s.setAttribute("LAST_SCAN_AT", Instant.now());
                    }

                    ServletContext ctx = request.getServletContext();
                    ctx.setAttribute(ATTR_LAST_SCAN_USER, username);
                    ctx.setAttribute(ATTR_LAST_SCAN_VALUE, scanVal);
                    ctx.setAttribute(ATTR_LAST_SCAN_NAME, productName);
                    ctx.setAttribute(ATTR_LAST_SCAN_AT, Instant.now());

                    if (uri != null && uri.startsWith(BARCODE_ENDPOINT_PREFIX)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> buf =
                                (List<Map<String, Object>>) ctx.getAttribute(ATTR_SCAN_TODAY);
                        if (buf == null) {
                            buf = new CopyOnWriteArrayList<>();
                            ctx.setAttribute(ATTR_SCAN_TODAY, buf);
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("time", LocalDateTime.now());
                        row.put("user", username);
                        row.put("value", scanVal);
                        row.put("name", productName);
                        row.put("traceId", cid);

                        buf.add(0, row);
                        while (buf.size() > 1000) buf.remove(buf.size() - 1);
                    }

                    try {
                        String pname = (productName == null || productName.isBlank()) ? "-" : productName;
                        ProductScanLog logRow = new ProductScanLog();
                        logRow.setUsername(username);
                        logRow.setValue(scanVal);
                        logRow.setProductName(pname);
                        logRow.setSource(resolveSource(request));
                        productScanLogRepository.save(logRow);
                    } catch (Exception e) {
                        log.warn("Gagal simpan product_scan_log: {}", e.toString());
                    }
                }

                log.info("API {} {} → status={} ({} ms) traceId={} user={}",
                        method, uri, status, dur, cid, username);
                if (!reqBody.isBlank()) log.info("reqBody: {}", reqBody);
                if (!resBody.isBlank()) log.info("resBody: {}", resBody);

            } finally {
                res.copyBodyToResponse();
                MDC.remove("traceId");
                MDC.remove("username");
            }
        }

        private static String getCid(HttpServletRequest req) {
            Object attr = req.getAttribute(ATTR_CID);
            if (attr instanceof String s && !s.isBlank()) return s;
            String h = req.getHeader(HDR_CID);
            return (h == null || h.isBlank()) ? "cid-missing" : h;
        }


        // === Username resolver ===================================================

        private String resolveUsername(HttpServletRequest request) {
            String username = "anonymous";

            // 1) Spring Security principal
            try {
                var auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                    String p = auth.getName();
                    if (p != null && !p.isBlank() && !"anonymousUser".equalsIgnoreCase(p)) {
                        username = p;
                    }
                }
            } catch (Exception ignored) {
            }

            // 2) Session attribute
            if ("anonymous".equals(username)) {
                HttpSession s = request.getSession(false);
                if (s != null && s.getAttribute("AUTH_USERNAME") != null) {
                    username = String.valueOf(s.getAttribute("AUTH_USERNAME"));
                }
            }

            // 3) IP→user map fallback
            if ("anonymous".equals(username)) {
                String clientIp = clientIpFromXffOrRemote(request);
                ServletContext ctx = request.getServletContext();
                @SuppressWarnings("unchecked")
                Map<String, String> ipUserMap = (Map<String, String>) ctx.getAttribute("IP_USER_MAP");
                if (ipUserMap != null) {
                    String mapped = ipUserMap.get(clientIp);
                    if (mapped == null) {
                        String alt = request.getHeader("X-Real-IP");
                        if (alt != null) mapped = ipUserMap.get(alt);
                    }
                    if (mapped != null) username = mapped;
                }
            }

            return username;
        }

        private ScanSource resolveSource(HttpServletRequest req) {
            String hdr = req.getHeader("X-Client-Source");
            if (hdr != null && hdr.equalsIgnoreCase("ANDROID")) return ScanSource.ANDROID;

            String src = req.getParameter("src");
            if (src != null && src.equalsIgnoreCase("ANDROID")) return ScanSource.ANDROID;


            String ua = req.getHeader("User-Agent");
            if (ua != null) {
                String ual = ua.toLowerCase();
                if (ual.contains("android") || ual.contains("okhttp") || ual.contains("dalvik"))
                    return ScanSource.ANDROID;
            }

            String uri = req.getRequestURI();
            if ("/api/auth/login".equals(uri)) {
                // UA bisa null di beberapa stack => treat as ANDROID untuk endpoint ini
                return ScanSource.ANDROID;
            }

            return ScanSource.WEB;
        }

        private static String clientIpFromXffOrRemote(HttpServletRequest req) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }

        // === Helpers =============================================================

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
            try {
                s = OM.writeValueAsString(OM.readTree(raw));
            } catch (Exception ignored) {
            }
            s = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ").trim();
            if (s.length() > MAX) s = s.substring(0, MAX) + "...(truncated)";
            return s;
        }

        private static String extractScanValue(String body) {
            if (body == null || body.isBlank()) return null;
            try {
                JsonNode n = OM.readTree(body);
                JsonNode v = n.get("value");
                if (v != null && !v.isNull()) return v.asText();
            } catch (Exception ignored) {
            }
            var m = P_VALUE.matcher(body);
            return m.find() ? m.group(1) : null;
        }

        private static String extractJsonField(String body, String field) {
            if (body == null || body.isBlank()) return null;
            try {
                JsonNode n = OM.readTree(body);
                JsonNode v = n.get(field);
                if (v != null && !v.isNull()) return v.asText();
            } catch (Exception ignored) {
            }
            Pattern p = "name".equals(field) ? P_NAME :
                    Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
            var m = p.matcher(body);
            return m.find() ? m.group(1) : null;
        }

    }


}
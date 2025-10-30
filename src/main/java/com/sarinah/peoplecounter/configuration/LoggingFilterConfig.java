package com.sarinah.peoplecounter.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarinah.peoplecounter.entity.ProductScanLog;
import com.sarinah.peoplecounter.entity.ScanSource;
import com.sarinah.peoplecounter.repository.ProductScanLogRepository;
import jakarta.servlet.*;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

// ====== GANTI sesuai paketmu ======
               // <-- sesuaikan
// ===================================

@Configuration
@RequiredArgsConstructor
public class LoggingFilterConfig {

    /* ===== Atribut global untuk UI/Thymeleaf ===== */
    public static final String ATTR_LAST_SCAN_USER  = "LAST_SCAN_USER_GLOBAL";
    public static final String ATTR_LAST_SCAN_VALUE = "LAST_SCAN_VALUE_GLOBAL";
    public static final String ATTR_LAST_SCAN_NAME  = "LAST_SCAN_NAME_GLOBAL";
    public static final String ATTR_LAST_SCAN_AT    = "LAST_SCAN_AT_GLOBAL";
    public static final String ATTR_SCAN_TODAY      = "SCAN_TODAY_LIST";

    /* ===== Correlation ===== */
    public static final String HDR_CID  = "X-Correlation-ID";
    public static final String ATTR_CID = "REQ_CID";

    /* ===== Cache CID → username (ServletContext) ===== */
    private static final String ATTR_CID_USER_MAP    = "CID_USER_MAP";
    private static final String ATTR_CID_USER_TS_MAP = "CID_USER_TS_MAP";
    private static final long CID_USER_TTL_MS = 6L * 60 * 60 * 1000; // 6 jam

    private final ProductScanLogRepository productScanLogRepository;

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
                                @Override public void onComplete(AsyncEvent e) { MDC.remove("cid"); }
                                @Override public void onTimeout(AsyncEvent e) {}
                                @Override public void onError(AsyncEvent e) {}
                                @Override public void onStartAsync(AsyncEvent e) {}
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
        var reg = new FilterRegistrationBean<ApiLoggingFilter>();
        reg.setFilter(filter);
        reg.setName("apiLoggingFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        return reg;
    }

    /* ===================================================================== */

    static class ApiLoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
        private static final ObjectMapper OM = new ObjectMapper();
        private static final int MAX = 4096;

        private final ProductScanLogRepository productScanLogRepository;

        ApiLoggingFilter(ProductScanLogRepository repo) { this.productScanLogRepository = repo; }

        /* Parsers */
        private static final Pattern P_VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern P_NAME  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

        /* Endpoints */
        private static final String LOGIN_ENDPOINT          = "/api/auth/login";
        private static final String BARCODE_ENDPOINT_PREFIX = "/sarinah-forwarder/v1/modul/barcode";

        @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
        @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            ContentCachingRequestWrapper  req = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            final String cid = getCid(req);
            MDC.put("traceId", cid); // kompat nama lama untuk log pattern lama

            long start = System.currentTimeMillis();
            try {
                chain.doFilter(req, res);

                String uri = req.getRequestURI();
                String username = resolveUsername(req); // principal/session

                // Jika login sukses tapi masih "anonymous", baca username dari body login
                if ("anonymous".equals(username) && LOGIN_ENDPOINT.equals(uri) && res.getStatus() < 400) {
                    String u = parseUsernameFromLogin(req);
                    if (u != null && !u.isBlank()) username = u;
                }

                // Login sukses → cache CID→username (TTL)
                if (LOGIN_ENDPOINT.equals(uri) && res.getStatus() < 400 && !"anonymous".equals(username)) {
                    cacheCidUser(req.getServletContext(), cid, username);
                }

                // Fallback umum → ambil dari cache CID→username
                if ("anonymous".equals(username)) {
                    String cached = lookupCidUser(req.getServletContext(), cid);
                    if (cached != null) username = cached;
                }

                MDC.put("username", username);

                int status = res.getStatus();
                long dur   = System.currentTimeMillis() - start;
                String method  = req.getMethod();
                String reqBody = sanitize(bytesToString(req.getContentAsByteArray()));
                String resBody = sanitize(bytesToString(res.getContentAsByteArray()));

                String scanVal     = extractScanValue(reqBody);
                String productName = extractJsonField(resBody, "name");

                if (scanVal != null && !scanVal.isBlank()) {
                    log.info("SCAN user={} value={} name={}", username, scanVal,
                            (productName == null || productName.isBlank()) ? "-" : productName);

                    // Session (untuk UI)
                    HttpSession s = req.getSession(false);
                    if (s != null) {
                        s.setAttribute("LAST_SCAN_USER",  username);
                        s.setAttribute("LAST_SCAN_VALUE", scanVal);
                        s.setAttribute("LAST_SCAN_NAME",  productName);
                        s.setAttribute("LAST_SCAN_AT",    Instant.now());
                    }

                    // ServletContext (untuk UI global)
                    ServletContext ctx = request.getServletContext();
                    ctx.setAttribute(ATTR_LAST_SCAN_USER,  username);
                    ctx.setAttribute(ATTR_LAST_SCAN_VALUE, scanVal);
                    ctx.setAttribute(ATTR_LAST_SCAN_NAME,  productName);
                    ctx.setAttribute(ATTR_LAST_SCAN_AT,    Instant.now());

                    if (uri != null && uri.startsWith(BARCODE_ENDPOINT_PREFIX)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> buf =
                                (List<Map<String, Object>>) ctx.getAttribute(ATTR_SCAN_TODAY);
                        if (buf == null) {
                            buf = new CopyOnWriteArrayList<>();
                            ctx.setAttribute(ATTR_SCAN_TODAY, buf);
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("time",  LocalDateTime.now());
                        row.put("user",  username);
                        row.put("value", scanVal);
                        row.put("name",  productName);
                        // >>> Tidak menaruh trace/cid di row untuk menghindari kebiasaan simpan trace
                        buf.add(0, row);
                        while (buf.size() > 1000) buf.remove(buf.size() - 1);
                    }

                    // Simpan ke DB (TANPA trace id)
                    try {
                        var src = resolveSource(request);
                        String pname = (productName == null || productName.isBlank()) ? "-" : productName;

                        var logRow =  new ProductScanLog(); // <-- sesuaikan paket Entity
                        logRow.setUsername(username);
                        logRow.setValue(scanVal);
                        logRow.setProductName(pname);
                        logRow.setSource(src);
                        productScanLogRepository.save(logRow);
                    } catch (Exception e) {
                        log.warn("Gagal simpan product_scan_log: {}", e.toString());
                    }
                }

                log.info("API {} {} → status={} ({} ms) cid={} user={}",
                        method, uri, status, dur, cid, username);
                if (!reqBody.isBlank()) log.info("reqBody: {}", reqBody);
                if (!resBody.isBlank()) log.info("resBody: {}", resBody);

            } finally {
                res.copyBodyToResponse();
                MDC.remove("traceId");
                MDC.remove("username");
            }
        }

        /* ===== Helpers umum ===== */

        private static String getCid(HttpServletRequest req) {
            Object attr = req.getAttribute(ATTR_CID);
            if (attr instanceof String s && !s.isBlank()) return s;
            String h = req.getHeader(HDR_CID);
            return (h == null || h.isBlank()) ? "cid-missing" : h;
        }

        private static void cacheCidUser(ServletContext ctx, String cid, String username) {
            @SuppressWarnings("unchecked")
            Map<String,String> map = (Map<String,String>) ctx.getAttribute(ATTR_CID_USER_MAP);
            if (map == null) { map = new ConcurrentHashMap<>(); ctx.setAttribute(ATTR_CID_USER_MAP, map); }
            map.put(cid, username);

            @SuppressWarnings("unchecked")
            Map<String,Long> ts = (Map<String,Long>) ctx.getAttribute(ATTR_CID_USER_TS_MAP);
            if (ts == null) { ts = new ConcurrentHashMap<>(); ctx.setAttribute(ATTR_CID_USER_TS_MAP, ts); }
            ts.put(cid, System.currentTimeMillis());
        }

        private static String lookupCidUser(ServletContext ctx, String cid) {
            if (cid == null) return null;
            @SuppressWarnings("unchecked")
            Map<String,String> map = (Map<String,String>) ctx.getAttribute(ATTR_CID_USER_MAP);
            @SuppressWarnings("unchecked")
            Map<String,Long> ts = (Map<String,Long>) ctx.getAttribute(ATTR_CID_USER_TS_MAP);
            if (map == null || ts == null) return null;

            String u = map.get(cid);
            Long t   = ts.get(cid);
            long now = System.currentTimeMillis();

            if (u != null && t != null && now - t <= CID_USER_TTL_MS) return u;

            // expired → bersihkan
            map.remove(cid);
            ts.remove(cid);
            return null;
        }

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
            } catch (Exception ignored) { }

            // 2) Session attribute
            if ("anonymous".equals(username)) {
                HttpSession s = request.getSession(false);
                if (s != null && s.getAttribute("AUTH_USERNAME") != null) {
                    username = String.valueOf(s.getAttribute("AUTH_USERNAME"));
                }
            }

            // 3) Fallback CID→username
            if ("anonymous".equals(username)) {
                String cid = getCid(request);
                String cached = lookupCidUser(request.getServletContext(), cid);
                if (cached != null) username = cached;
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
            return ScanSource.WEB;
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

        private static String extractScanValue(String body) {
            if (body == null || body.isBlank()) return null;
            try {
                JsonNode n = OM.readTree(body);
                JsonNode v = n.get("value");
                if (v != null && !v.isNull()) return v.asText();
            } catch (Exception ignored) {}
            var m = P_VALUE.matcher(body);
            return m.find() ? m.group(1) : null;
        }

        private static String extractJsonField(String body, String field) {
            if (body == null || body.isBlank()) return null;
            try {
                JsonNode n = OM.readTree(body);
                JsonNode v = n.get(field);
                if (v != null && !v.isNull()) return v.asText();
            } catch (Exception ignored) {}
            Pattern p = "name".equals(field) ? P_NAME :
                    Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
            var m = p.matcher(body);
            return m.find() ? m.group(1) : null;
        }

        private static String parseUsernameFromLogin(ContentCachingRequestWrapper req) {
            String body = bytesToString(req.getContentAsByteArray());
            if (body == null || body.isBlank()) return null;

            try { // JSON
                JsonNode n = OM.readTree(body);
                if (n.hasNonNull("username")) return n.get("username").asText();
            } catch (Exception ignore) { }

            // x-www-form-urlencoded
            for (String part : body.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 &&
                        "username".equalsIgnoreCase(URLDecoder.decode(kv[0], StandardCharsets.UTF_8))) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}


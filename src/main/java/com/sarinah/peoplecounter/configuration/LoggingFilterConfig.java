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
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Logging + CID binding (username <-> X-Correlation-ID)
 * - CID filter dipasang paling awal (ORDER: HIGHEST_PRECEDENCE)
 * - Binding filter setelah security: isi/refresh cache CID->username
 * - ApiLoggingFilter setelah security juga: pakai cache untuk resolve username,
 *   simpan scan log, simpan LAST_SCAN_* ke session & servlet context, dll.
 */
@Configuration
@RequiredArgsConstructor
public class LoggingFilterConfig {

    // ====== Keys / headers ======
    public static final String ATTR_LAST_SCAN_USER  = "LAST_SCAN_USER_GLOBAL";
    public static final String ATTR_LAST_SCAN_VALUE = "LAST_SCAN_VALUE_GLOBAL";
    public static final String ATTR_LAST_SCAN_NAME  = "LAST_SCAN_NAME_GLOBAL";
    public static final String ATTR_LAST_SCAN_AT    = "LAST_SCAN_AT_GLOBAL";
    public static final String ATTR_SCAN_TODAY      = "SCAN_TODAY_LIST";

    public static final String HDR_CID  = "X-Correlation-ID";
    public static final String ATTR_CID = "REQ_CID";

    private final ProductScanLogRepository productScanLogRepository;

    // ====== CID -> Username TTL Cache (sederhana, tanpa dependency) ======
    static class TtlMap {
        private static class Entry {
            final String user;
            volatile long at;
            Entry(String u) { this.user = u; this.at = System.currentTimeMillis(); }
        }
        private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
        private final long ttlMs;

        TtlMap(long ttlMs) { this.ttlMs = ttlMs; }

        void put(String cid, String user) {
            if (cid == null || cid.isBlank() || user == null || user.isBlank()) return;
            map.put(cid, new Entry(user));
        }
        String get(String cid) {
            if (cid == null || cid.isBlank()) return null;
            Entry e = map.get(cid);
            if (e == null) return null;
            if (System.currentTimeMillis() - e.at > ttlMs) {
                map.remove(cid);
                return null;
            }
            return e.user;
        }
        void refresh(String cid) {
            Entry e = map.get(cid);
            if (e != null) e.at = System.currentTimeMillis();
        }
        void invalidate(String cid) {
            if (cid == null || cid.isBlank()) return;
            map.remove(cid);
        }
    }

    @Bean
    public TtlMap cidUserCache() {
        // TTL 24 jam, silakan sesuaikan kebutuhan
        return new TtlMap(24L * 60 * 60 * 1000);
    }

    // ====== Beans ======
    @Bean
    public ApiLoggingFilter apiLoggingFilter(TtlMap cidUserCache) {
        return new ApiLoggingFilter(productScanLogRepository, cidUserCache);
    }

    /**
     * Filter CID paling awal → header tersedia untuk semua layer.
     */
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
                } finally {
                    MDC.remove("cid");
                }
            }
        };
    }

    /**
     * Binding filter — setelah security, sebelum logging:
     * - Jika ada auth principal (SecurityContext) → ikat CID->username
     * - Jika tidak ada, tapi ada header hint X-Authenticated-User → ikat sementara
     */
    @Bean
    @Order(SecurityProperties.DEFAULT_FILTER_ORDER + 2) // -100 + 2 = -98 (sebelum ApiLoggingFilter -99)
    public Filter cidUserBindingFilter(TtlMap cidUserCache) {
        return new OncePerRequestFilter() {
            @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
            @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                try {
                    String cid = Optional.ofNullable((String) request.getAttribute(ATTR_CID))
                            .filter(s -> !s.isBlank())
                            .orElse(request.getHeader(HDR_CID));

                    // Ambil dari SecurityContext jika ada
                    String authUser = null;
                    try {
                        var auth = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication();
                        if (auth != null && auth.isAuthenticated()
                                && auth.getName() != null
                                && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
                            authUser = auth.getName();
                        }
                    } catch (Exception ignored) {}

                    // Kalau belum ada, pakai header hint (opsional dari reverse proxy / app)
                    if (authUser == null || authUser.isBlank()) {
                        authUser = Optional.ofNullable(request.getHeader("X-Authenticated-User"))
                                .filter(s -> !s.isBlank())
                                .orElse(null);
                    }

                    // Ikat atau refresh
                    if (cid != null && !cid.isBlank() && authUser != null && !authUser.isBlank()) {
                        cidUserCache.put(cid, authUser);
                    } else if (cid != null && !cid.isBlank()) {
                        // Tidak ada user baru → kalau sudah ada mapping, tetap refresh TTL agar tidak cepat kedaluwarsa
                        cidUserCache.refresh(cid);
                    }
                } finally {
                    chain.doFilter(request, response);
                }
            }
        };
    }

    /**
     * Penting: Jalankan logging filter SETELAH Spring Security.
     * Security chain ~ -100. Kita pasang di -99.
     */
    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> loggingFilterRegistration(ApiLoggingFilter filter) {
        FilterRegistrationBean<ApiLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.setName("apiLoggingFilter");
        try {
            int AFTER_SECURITY = SecurityProperties.DEFAULT_FILTER_ORDER + 1; // -99
            reg.setOrder(AFTER_SECURITY);
        } catch (Throwable ignore) {
            reg.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        }
        reg.addUrlPatterns("/*");
        reg.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        return reg;
    }

    // ====== Filter implementasi ======
    static class ApiLoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
        private static final ObjectMapper OM = new ObjectMapper();
        private static final int MAX = 4096;

        private final ProductScanLogRepository productScanLogRepository;
        private final TtlMap cidUserCache;

        ApiLoggingFilter(ProductScanLogRepository repo, TtlMap cidUserCache) {
            this.productScanLogRepository = repo;
            this.cidUserCache = cidUserCache;
        }

        private static final Pattern P_VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern P_NAME  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

        private static final String BARCODE_ENDPOINT_PREFIX = "/sarinah-forwarder/v1/modul/barcode";

        @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
        @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            // Bungkus supaya body masih bisa dibaca setelah chain
            ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            final String cid = getCid(req);
            MDC.put("traceId", cid); // kompat nama lama

            long start = System.currentTimeMillis();
            try {
                chain.doFilter(req, res);

                // >>> Pada titik ini kita BERADA SETELAH Spring Security
                String username = resolveUsername(req);

                long dur = System.currentTimeMillis() - start;
                String method = req.getMethod();
                String uri = req.getRequestURI();
                int status = res.getStatus();

                String reqBody = sanitize(bytesToString(req.getContentAsByteArray()));
                String resBody = sanitize(bytesToString(res.getContentAsByteArray()));

                String scanVal     = extractScanValue(reqBody);
                String productName = extractJsonField(resBody, "name");

                if (scanVal != null && !scanVal.isBlank()) {
                    log.info("SCAN user={} value={} name={}", username, scanVal,
                            (productName == null || productName.isBlank()) ? "-" : productName);

                    HttpSession s = req.getSession(false);
                    if (s != null) {
                        s.setAttribute(ATTR_LAST_SCAN_USER,  username);
                        s.setAttribute(ATTR_LAST_SCAN_VALUE, scanVal);
                        s.setAttribute(ATTR_LAST_SCAN_NAME,  productName);
                        s.setAttribute(ATTR_LAST_SCAN_AT,    Instant.now());
                    }

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
                        row.put("time",     LocalDateTime.now());
                        row.put("user",     username);
                        row.put("value",    scanVal);
                        row.put("name",     productName);
                        row.put("traceId",  cid);

                        buf.add(0, row);
                        while (buf.size() > 1000) buf.remove(buf.size() - 1);
                    }

                    try {
                        ProductScanLog logRow = new ProductScanLog();
                        logRow.setUsername(username);
                        logRow.setValue(scanVal);
                        logRow.setProductName((productName == null || productName.isBlank()) ? "-" : productName);
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

        // === Username resolver berbasis CID cache (tanpa IP fallback) ===
        private String resolveUsername(HttpServletRequest request) {
            // 0) Spring Security principal (kalau ada)
            try {
                var auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    String name = auth.getName();
                    if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
                        MDC.put("username", name);
                        // Perkuat binding ke CID saat ini
                        String cid = getCid(request);
                        if (!"cid-missing".equals(cid)) cidUserCache.put(cid, name);
                        return name;
                    }
                }
            } catch (Exception ignored) {}

            // 1) Servlet container principal
            if (request.getUserPrincipal() != null &&
                    request.getUserPrincipal().getName() != null &&
                    !request.getUserPrincipal().getName().isBlank()) {
                String name = request.getUserPrincipal().getName();
                MDC.put("username", name);
                String cid = getCid(request);
                if (!"cid-missing".equals(cid)) cidUserCache.put(cid, name);
                return name;
            }

            // 2) Session attribute (untuk klien web stateful)
            HttpSession s = request.getSession(false);
            if (s != null && s.getAttribute("AUTH_USERNAME") != null) {
                String name = String.valueOf(s.getAttribute("AUTH_USERNAME"));
                if (!name.isBlank()) {
                    MDC.put("username", name);
                    String cid = getCid(request);
                    if (!"cid-missing".equals(cid)) cidUserCache.put(cid, name);
                    return name;
                }
            }

            // 3) CID → username cache (inti untuk Android/stateless)
            String cid = getCid(request);
            String cached = cidUserCache.get(cid);
            if (cached != null && !cached.isBlank()) {
                MDC.put("username", cached);
                return cached;
            }

            // 4) Hint dari header (opsional sebagai last resort)
            String hinted = Optional.ofNullable(request.getHeader("X-Authenticated-User"))
                    .filter(s2 -> !s2.isBlank()).orElse(null);
            if (hinted != null) {
                MDC.put("username", hinted);
                if (!"cid-missing".equals(cid)) cidUserCache.put(cid, hinted);
                return hinted;
            }

            MDC.put("username", "anonymous");
            return "anonymous";
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

            if ("/api/auth/login".equals(req.getRequestURI())) {
                return ScanSource.ANDROID;
            }
            return ScanSource.WEB;
        }

        // ===== Helpers =====
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
    }
}

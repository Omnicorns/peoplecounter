package com.sarinah.peoplecounter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.adaptor.SarinahGetModulAdaptor;
import com.sarinah.peoplecounter.entity.User;
import com.sarinah.peoplecounter.entity.UserStatus;
import com.sarinah.peoplecounter.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.toCollection;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final UserRepository userRepo;
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;
    private final ObjectMapper om;                                // untuk bikin ObjectNode


    // Nama atribut session untuk menandai user sudah login
    public static final String AUTH_USER_ID = "AUTH_USER_ID";
    public static final String AUTH_USERNAME = "AUTH_USERNAME";
    public static final String AUTH_FULLNAME = "AUTH_FULLNAME";
    private static final String SESSION_SKU_HISTORY = "SKU_HISTORY";

    @GetMapping("/")
    public String loginPage(@RequestParam(value = "redirect", required = false) String redirect,
                            Model model,
                            HttpSession session) {
        // Jika sudah login, langsung lempar ke home/dashboard
        if (session.getAttribute(AUTH_USER_ID) != null) {
            return "redirect:" + (redirect != null && !redirect.isBlank() ? redirect : "/web/product");
        }
        model.addAttribute("error", null);
        model.addAttribute("redirect", redirect);
        return "web-login";
    }

    @PostMapping("/web/login")
    public String doLogin(@RequestParam("usernameOrEmail") String usernameOrEmail,
                          @RequestParam("password") String password,
                          @RequestParam(value = "redirect", required = false) String redirect,
                          Model model,
                          HttpSession session) {

        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isBlank()) {
            model.addAttribute("error", "Username/Email dan Password wajib diisi.");
            model.addAttribute("redirect", redirect);
            return "web-login";
        }

        Optional<User> userOpt = userRepo.findByUsername(usernameOrEmail.toLowerCase());
        if (userOpt.isEmpty()) {
            userOpt = userRepo.findByEmail(usernameOrEmail.toLowerCase());
        }
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User tidak ditemukan.");
            model.addAttribute("redirect", redirect);
            return "web-login";
        }

        User user = userOpt.get();

        if (user.getStatus() != UserStatus.ACTIVE) {
            model.addAttribute("error", "User inactive.");
            model.addAttribute("redirect", redirect);
            return "web-login";
        }

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            model.addAttribute("error", "Password salah.");
            model.addAttribute("redirect", redirect);
            return "web-login";
        }

        // sukses: set session
        session.setAttribute(AUTH_USER_ID, user.getId().toString());
        session.setAttribute(AUTH_USERNAME, user.getUsername());
        session.setAttribute(AUTH_FULLNAME, user.getFullName());

        // arahkan ke redirect (jika ada) atau ke dashboard (/)
        String target = (redirect != null && !redirect.isBlank()) ? redirect : "/web/product";
        return "redirect:" + target;
    }

    @PostMapping("/web/logout")
    public String doLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }


    @GetMapping("/web/product")
    public String productPage(@RequestParam(value = "sku", required = false) String sku,
                              @RequestParam(value = "code", required = false) String code,
                              Model model,
                              HttpServletRequest request,
                              HttpSession session) {

        if (session.getAttribute(AUTH_USER_ID) == null) {
            // simpan url tujuan (termasuk query) agar bisa balik setelah login
            String full = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String redirect = UriUtils.encode(full, StandardCharsets.UTF_8);
            return "redirect:/web/login?redirect=" + redirect;
        }


        // untuk ngisi ulang form
        model.addAttribute("qSku", sku);
        model.addAttribute("qCode", code);

        // kalau kosong, render form saja
        if ((sku == null || sku.isBlank()) && (code == null || code.isBlank())) {
            return "product-detail";
        }

        // request ke adaptor — kamu bebas pakai "barcode" atau "sku" sesuai endpoint
        ObjectNode req = om.createObjectNode();
        if (code != null && !code.isBlank()) req.put("value", code);
        if (sku  != null && !sku.isBlank())  req.put("value", sku);

        ObjectNode root = sarinahGetModulAdaptor.getScanBarcode(req);

        // ====== MAPPING SESUAI JSON CONTOH ======
        // name
        String name = textOrDefault(root.get("name"), "-");

        // sku: di JSON namanya "default_code"
        String matchingSku ;
        String outSku = textOrNull(root.get("default_code"));
        if (outSku == null) outSku = "-"; // fallback
        matchingSku = outSku;

        // promo: "promotion_ids" adalah MAP: id -> {name: "..."}
        String promo = "-";
        double productDiscount = 0;
        // Asumsi variabel tersedia:
// JsonNode root;                    // JSON respons produk
// String sku = /* default_code */;  // contoh: "BEA0016361"
// String promo = null;              // output gabungan nama promo
// Double productDiscount = null;    // output diskon produk (persen), null kalau tidak ada

        JsonNode promoNode = root.get("promotion_ids");
        if (promoNode != null && promoNode.isObject() && promoNode.size() > 0) {
            List<String> promos = new ArrayList<>();
            AtomicReference<Double> maxDisc = new AtomicReference<>(Double.NEGATIVE_INFINITY);

            promoNode.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();

                // Kumpulkan nama promo
                String nm = textOrNull(v.get("name"));
                if (nm != null && !nm.isBlank()) promos.add(nm);

                // Cari diskon produk (hanya dari products_discount) yang match SKU di setiap condition
                JsonNode conditions = v.path("conditions");
                if (conditions.isObject() && conditions.size() > 0) {
                    conditions.fields().forEachRemaining(condEntry -> {
                        JsonNode cond = condEntry.getValue();

                        JsonNode productIds = cond.path("product_ids");
                        if (!(productIds.isArray() && productIds.size() > 0)) return;

                        // product_ids berformat: "[<SKU>] Nama Produk ..."
                        boolean matched = false;
                        for (JsonNode pidNode : productIds) {
                            String pid = pidNode.asText("");
                            if (pid.startsWith("[" + matchingSku + "]") || pid.contains("[" + matchingSku + "]")) {
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) return;

                        double dMain = cond.path("products_discount").asDouble(Double.NaN);
                        if (!Double.isNaN(dMain) && dMain > maxDisc.get()) {
                            maxDisc.set(dMain);
                        }
                    });
                }
            });

            if (!promos.isEmpty()) {
                promo = String.join("; ", promos);
            }
            if (maxDisc.get() != Double.NEGATIVE_INFINITY) {
                productDiscount = maxDisc.get(); // contoh: 85.0
            }
        }




        // stok per lokasi: "stock_by_location" adalah MAP: id -> { location, quantity, price, ... }
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode stockMap = root.get("stock_by_location");
        if (stockMap != null && stockMap.isObject()) {
            stockMap.fields().forEachRemaining(e -> {
                JsonNode n = e.getValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("location", textOrDefault(n.get("location"), "-"));
                m.put("stock",    intOrZero(n.get("quantity")));
                m.put("uom",      textOrDefault(n.get("uom"), ""));
                m.put("price",    decimalOrZero(n.get("price")));         // BigDecimal
                m.put("pricelist",textOrDefault(n.get("pricelist_name"), ""));
                rows.add(m);
            });
        }

        // base/list price (opsional ditampilkan)
        BigDecimal listPrice = decimalOrZero(root.get("list_price"));

        // kirim ke view (tanpa VM)
        model.addAttribute("name", name);
        model.addAttribute("sku", outSku);
        model.addAttribute("promo", promo);
        model.addAttribute("listPrice", listPrice);
        model.addAttribute("stocks", rows);
        model.addAttribute("productDiscount", productDiscount);

        // link lanjutan kalau ada halaman lain
        model.addAttribute("stockPriceUrl", "/web/product/" + outSku + "/stock");

        // === PANGGIL INI JIKA DATA VALID ===
        if (outSku != null && !outSku.isBlank()
                && name != null && !name.isBlank()
                && !name.trim().equals("-")
                && rows != null && !rows.isEmpty()) {
            addSkuHistory(session, outSku, name);
        }
      

// SELALU kirim history ke view
   model.addAttribute("history", getSkuHistory(session));

        return "product-detail";
    }

    @PostMapping("/web/product/history/clear")
    public String clearHistory(HttpSession session) {
        session.removeAttribute(SESSION_SKU_HISTORY);
        return "redirect:/web/product";
    }


    // helpers
    private static String textOrNull(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText(null);
    }
    private static String textOrDefault(JsonNode n, String def) {
        return (n == null || n.isMissingNode() || n.isNull()) ? def : n.asText(def);
    }
    private static int intOrZero(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull()) ? 0 : n.asInt(0);
    }
    private static BigDecimal decimalOrZero(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO;
        try { return new BigDecimal(n.asText()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    @SuppressWarnings("unchecked")
    private void addSkuHistory(HttpSession session, String sku, String name) {
        if (sku == null || sku.isBlank() || name.trim().equals("-")) return;


        if (name == null || name.isBlank() || "-".equals(name.trim())) return;

        List<Map<String,String>> hist =
                (List<Map<String,String>>) session.getAttribute(SESSION_SKU_HISTORY);
        if (hist == null) hist = new LinkedList<>();

        // buang entry lama dengan sku yang sama
        hist = hist.stream()
                .filter(m -> !sku.equalsIgnoreCase(m.getOrDefault("sku","")))
                .collect(toCollection(LinkedList::new));

        // tambah di depan
        Map<String,String> entry = new HashMap<>();
        entry.put("sku", sku);
        entry.put("name", name.trim());
        hist.add(0, entry);

        // batas 10
        if (hist.size() > 10) hist = new LinkedList<>(hist.subList(0, 10));

        session.setAttribute(SESSION_SKU_HISTORY, hist);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,String>> getSkuHistory(HttpSession session){

        List<Map<String,String>> hist =
                (List<Map<String,String>>) session.getAttribute(SESSION_SKU_HISTORY);

        return hist != null ? hist : Collections.emptyList();
    }


}


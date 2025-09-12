package com.sarinah.peoplecounter.service;

import com.sarinah.peoplecounter.entity.User;
import com.sarinah.peoplecounter.entity.UserStatus;
import com.sarinah.peoplecounter.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserImportService {
    private final UserRepository repo;

    public record RowResult(int row, String email, String username, String action, String note) {}
    public record UploadResult(int inserted, int updated, int skipped, List<RowResult> details) {}

    @Transactional
    public UploadResult importCsv(InputStream in) throws IOException {
        int ins = 0, upd = 0, skip = 0;
        List<RowResult> det = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("CSV kosong");

            // --- detect separator & build header index (normalized) ---
            char sep = detectSep(header);               // ';' | ',' | '\t'
            Map<String, Integer> h = buildHeaderIndex(header, sep);

            // precompute indices with aliases (bebas bahasa/penamaan)
            final int ixEmail    = findIndex(h, "email","e-mail","mail");
            final int ixFullName = findIndex(h, "fullname","full_name","name","nama");
            final int ixUsername = findIndex(h, "username","user","login");
            final int ixPhone    = findIndex(h, "phone","hp","nohp","telp","telepon","mobile");
            final int ixPassword = findIndex(h, "password","pass","kata_sandi","katasandi");
            final int ixStatus   = findIndex(h, "status");
            final int ixKet      = findIndex(h, "keterangan","remark","notes","note","brand");

            String line; int row = 1;
            while ((line = br.readLine()) != null) {
                row++;
                if (line.isBlank()) { skip++; det.add(new RowResult(row, null, null, "SKIP", "blank")); continue; }

                String[] c = splitCsv(line, sep);

                try {
                    String email    = val(c, ixEmail);         // boleh null
                    String fullName = val(c, ixFullName);       // boleh null (kalau mau wajib, validasi)
                    String username = val(c, ixUsername);
                    String phone    = val(c, ixPhone);
                    String password = val(c, ixPassword);
                    String status   = Optional.ofNullable(val(c, ixStatus)).orElse("ACTIVE");
                    String ket      = val(c, ixKet);

                    if (username == null || password == null) {
                        skip++; det.add(new RowResult(row, email, username, "SKIP", "username/password null"));
                        continue;
                    }

                    UserStatus st = parseStatus(status);

                    // --- lookup aman (email opsional) ---
                    User u = null;
                    if (email != null) {
                        u = repo.findByEmailIgnoreCase(email).orElse(null);
                    }
                    if (u == null) {
                        u = repo.findByUsernameIgnoreCase(username).orElse(null);
                    }

                    boolean isNew = (u == null);
                    if (isNew) {
                        u = new User();
                        u.setCreatedAt(Instant.now()); // pakai OffsetDateTime.now() jika entity OffsetDateTime
                    }

                    u.setUpdatedAt(Instant.now());     // idem catatan di atas
                    u.setEmail(email);
                    u.setFullName(fullName);
                    u.setUsername(username);
                    u.setPhone(phone);
                    u.setStatus(st);
                    u.setKeterangan(ket);

                    if (password != null && !password.isBlank()) {
                        u.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt(10)));
                    }
                    if (u.getTermsAcceptedAt() == null && u.getStatus() == UserStatus.ACTIVE) {
                        u.setTermsAcceptedAt(Instant.now());
                    }

                    repo.save(u);
                    if (isNew) { ins++; det.add(new RowResult(row, email, username, "INSERT", "ok")); }
                    else       { upd++; det.add(new RowResult(row, email, username, "UPDATE", "ok")); }

                } catch (Exception e) {
                    skip++; det.add(new RowResult(row, null, null, "SKIP", "err: " + e.getMessage()));
                }
            }
        }
        return new UploadResult(ins, upd, skip, det);
    }


    // ===== JSON =====
    public static record JsonRow(
            String email, String fullName, String username, String phone,
            String password, String status, String keterangan) {}

    @Transactional
    public UploadResult importJson(List<JsonRow> rows) {
        int ins=0, upd=0, skip=0; List<RowResult> det=new ArrayList<>(); int row=1;
        for (JsonRow r : rows) {
            try {
                if (r.email()==null || r.username()==null || r.password()==null) {
                    skip++; det.add(new RowResult(row,r.email(),r.username(),"SKIP","email/username/password null")); row++; continue;
                }
                UserStatus st = parseStatus(Optional.ofNullable(r.status()).orElse("ACTIVE"));

                Optional<User> byEmail = repo.findByEmailIgnoreCase(r.email());
                Optional<User> byUser  = repo.findByUsernameIgnoreCase(r.username());
                User u = byEmail.or(() -> byUser).orElse(null);
                boolean isNew = (u==null);
                if (isNew) { u = new User(); u.setCreatedAt(Instant.now()); }

                u.setUpdatedAt(Instant.now());
                u.setEmail(r.email()); u.setFullName(r.fullName());
                u.setUsername(r.username()); u.setPhone(r.phone());
                u.setStatus(st); u.setKeterangan(r.keterangan());

                if (r.password()!=null && !r.password().isBlank()) {
                    u.setPasswordHash(BCrypt.hashpw(r.password(), BCrypt.gensalt(10)));
                }
                if (u.getTermsAcceptedAt()==null && u.getStatus()==UserStatus.ACTIVE)
                    u.setTermsAcceptedAt(Instant.now());

                repo.save(u);
                if (isNew) { ins++; det.add(new RowResult(row,r.email(),r.username(),"INSERT","ok")); }
                else { upd++; det.add(new RowResult(row,r.email(),r.username(),"UPDATE","ok")); }
            } catch (Exception e) {
                skip++; det.add(new RowResult(row,r.email(),r.username(),"SKIP","err: "+e.getMessage()));
            }
            row++;
        }
        return new UploadResult(ins,upd,skip,det);
    }

    // ===== helpers =====
    private static Map<String,Integer> index(String header) {
        String[] cols = header.toLowerCase(Locale.ROOT).split("\\s*,\\s*");
        Map<String,Integer> idx=new HashMap<>();
        for (int i=0;i<cols.length;i++) idx.put(cols[i], i);
        // make optional keys present with -1
        for (String k : List.of("email","full_name","username","phone","password","status","keterangan"))
            idx.putIfAbsent(k,-1);
        return idx;
    }
    private static String val(String[] arr, Integer i){
        if(i==null||i<0||i>=arr.length) return null; String s=arr[i].trim(); return s.isEmpty()?null:s;
    }
    private static Optional<String> opt(String[] arr, Integer i){ return Optional.ofNullable(val(arr,i)); }
    private static UserStatus parseStatus(String s){
        try { return UserStatus.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch(Exception e){ return UserStatus.SUSPENDED; }
    }
    private static String[] splitCsv(String line, char sep) {
        List<String> out=new ArrayList<>(); StringBuilder cur=new StringBuilder(); boolean quoted=false;
        for (int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if(ch=='"'){ if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){cur.append('"'); i++;} else quoted=!quoted; }
            else if(ch==sep && !quoted){ out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString()); return out.toArray(new String[0]);
    }

    private static String norm(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static char detectSep(String header) {
        if (header.indexOf(';') >= 0) return ';';
        if (header.indexOf('\t') >= 0) return '\t';
        return ','; // default
    }

    private static Map<String,Integer> buildHeaderIndex(String header, char sep) {
        String[] cols = splitCsv(header.replaceFirst("^\uFEFF", ""), sep); // strip BOM
        Map<String,Integer> idx = new HashMap<>();
        for (int i = 0; i < cols.length; i++) idx.put(norm(cols[i]), i);
        return idx;
    }

    private static int findIndex(Map<String,Integer> idx, String... aliases) {
        for (String a : aliases) {
            Integer i = idx.get(norm(a));
            if (i != null) return i;
        }
        return -1; // not found
    }

    private static String val(String[] arr, int i) {
        if (i < 0 || i >= arr.length) return null;
        String s = arr[i].trim();
        return s.isEmpty() ? null : s;
    }

}

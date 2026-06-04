package com.sarinah.peoplecounter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;

import javax.naming.directory.Attributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LdapUserService {

    @Autowired(required = false)
    private LdapTemplate ldapTemplate;

    @Autowired(required = false)
    private LdapContextSource ldapContextSource;

    @Value("${ldap.base-dn:}")
    private String baseDn;

    @Value("${ldap.user-search-base:}")
    private String userSearchBase;

    // AD pakai sAMAccountName; OpenLDAP ganti jadi (uid={0})
    @Value("${ldap.user-filter:(sAMAccountName={0})}")
    private String userFilter;

    public record LdapUser(String username, String displayName, String email, String source) {}

    // ---- Login ----

    public LoginResponse authenticate(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            return new LoginResponse(false, username, null, null,
                    "Username dan password wajib diisi");
        }

        if (!authenticateLdap(username, password)) {
            return new LoginResponse(false, username, null, null,
                    "Username atau password salah");
        }

        LdapUser detail = findLdapDetail(username);
        return new LoginResponse(
                true,
                username,
                detail != null ? detail.displayName() : username,
                detail != null ? detail.email() : null,
                "Login berhasil"
        );
    }

    /** Verifikasi password dengan bind ke LDAP/AD pakai kredensial user. */
    private boolean authenticateLdap(String username, String rawPassword) {
        if (ldapTemplate == null) {
            log.warn("LdapTemplate belum dikonfigurasi");
            return false;
        }
        try {
            String base = (userSearchBase != null && !userSearchBase.isBlank())
                    ? userSearchBase : "";
            String filter = userFilter.replace("{0}", encode(username));
            return ldapTemplate.authenticate(base, filter, rawPassword);
        } catch (Exception e) {
            log.warn("Gagal auth LDAP untuk {}: {}", username, e.getMessage());
            return false;
        }
    }

    /** Ambil detail user dari LDAP untuk dikembalikan ke response. */
    private LdapUser findLdapDetail(String username) {
        try {
            String filter = userFilter.replace("{0}", encode(username));
            List<LdapUser> hasil = ldapTemplate.search(
                    userSearchBase, filter,
                    (AttributesMapper<LdapUser>) attrs -> new LdapUser(
                            getAttr(attrs, "sAMAccountName"),
                            getAttr(attrs, "displayName"),
                            getAttr(attrs, "mail"),
                            "LDAP"));
            return hasil.isEmpty() ? null : hasil.get(0);
        } catch (Exception e) {
            log.warn("Gagal ambil detail LDAP untuk {}: {}", username, e.getMessage());
            return null;
        }
    }

    // ---- Helper ----

    private static String getAttr(Attributes attrs, String name) {
        try {
            var a = attrs.get(name);
            return a != null ? String.valueOf(a.get()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Escape karakter khusus LDAP -> cegah LDAP injection
    private static String encode(String s) {
        return org.springframework.ldap.support.LdapEncoder.filterEncode(s);
    }


    public record LoginRequest(String username, String password) {}

    public record LoginResponse(
            boolean success,
            String username,
            String displayName,
            String email,
            String message
    ) {}
}
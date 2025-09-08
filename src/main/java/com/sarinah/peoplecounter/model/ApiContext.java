package com.sarinah.peoplecounter.model;

public class ApiContext {
    private static volatile String lastLoginUser;

    public static void setUsername(String username) {
        lastLoginUser = username;
    }
    public static String getUsername() {
        return lastLoginUser != null ? lastLoginUser : "anonymous";
    }
}

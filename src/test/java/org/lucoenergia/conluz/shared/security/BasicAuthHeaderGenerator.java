package org.lucoenergia.conluz.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthHeaderGenerator {

    public static String generate(String username, String password) {
        String authString = username + ":" + password;
        byte[] authBytes = authString.getBytes(StandardCharsets.UTF_8);
        String encodedAuthString = Base64.getEncoder().encodeToString(authBytes);
        return "Basic " + encodedAuthString;
    }
}

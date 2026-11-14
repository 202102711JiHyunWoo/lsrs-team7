package kr.ac.cnu.lsrs.service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class TokenHash {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokenHash() { }
    public static String random() {
        byte[] data = new byte[32]; RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
    public static String sha256(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static boolean matches(String token, String expectedHash) {
        return MessageDigest.isEqual(sha256(token).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}

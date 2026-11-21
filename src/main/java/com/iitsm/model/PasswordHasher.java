package com.iitsm.model;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2-SHA256 password hashing. */
public final class PasswordHasher {
    private PasswordHasher() { }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(derive(password, salt));
    }

    public static boolean verify(String password, String stored) {
        String[] parts = stored.split("\\$");
        return MessageDigest.isEqual(derive(password, HexFormat.of().parseHex(parts[0])),
                HexFormat.of().parseHex(parts[1]));
    }

    private static byte[] derive(String password, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 100_000, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

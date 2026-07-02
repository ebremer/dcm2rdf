package com.ebremer.dcm2rdf.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class HashGeneratorUtils {

    private HashGeneratorUtils() {
    }

    public static String generateSHA256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is a mandatory JCA algorithm; a JVM without it is unusable here
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

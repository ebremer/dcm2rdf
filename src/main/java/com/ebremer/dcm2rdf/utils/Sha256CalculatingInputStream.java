package com.ebremer.dcm2rdf.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Sha256CalculatingInputStream extends FilterInputStream {
    private final MessageDigest messageDigest;
    private byte[] finalHash = null;

    public Sha256CalculatingInputStream(InputStream in) throws NoSuchAlgorithmException {
        super(in);
        messageDigest = MessageDigest.getInstance("SHA-256");
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            messageDigest.update((byte) b);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n != -1) {
            messageDigest.update(b, off, n);
        }
        return n;
    }

    public String getSha256Hash() {
        if (finalHash == null) {
            finalHash = messageDigest.digest();
        }
        return HexFormat.of().formatHex(finalHash);
    }
}

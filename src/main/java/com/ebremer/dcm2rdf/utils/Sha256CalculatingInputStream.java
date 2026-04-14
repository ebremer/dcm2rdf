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
    private byte[] skipBuffer = null;

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

    @Override
    public long skip(long n) throws IOException {
        // skip() on the underlying stream would bypass the digest (dcm4che skips excluded
        // bulk data this way), so consume the bytes through read() instead
        if (skipBuffer == null) {
            skipBuffer = new byte[8192];
        }
        long remaining = n;
        while (remaining > 0) {
            int r = read(skipBuffer, 0, (int) Math.min(skipBuffer.length, remaining));
            if (r == -1) {
                break;
            }
            remaining -= r;
        }
        return n - remaining;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readlimit) {
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported: rewinding would corrupt the digest");
    }

    public String getSha256Hash() {
        if (finalHash == null) {
            finalHash = messageDigest.digest();
        }
        return HexFormat.of().formatHex(finalHash);
    }
}
